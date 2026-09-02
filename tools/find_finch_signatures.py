#!/usr/bin/env python3
"""
Helper tool to extract ARM64 native signatures for Ilqfk Plus patch from Ilqfk's libapp.so, APK, or APKM.

Usage:
    python tools/find_ilqfk_signatures.py [path_to_libapp.so_or_apk_or_apkm] [version_string]

Example:
    python tools/find_ilqfk_signatures.py libapp.so 3.74.0
    python tools/find_ilqfk_signatures.py ilqfk_3.74.0.apk
    python tools/find_ilqfk_signatures.py ilqfk_3.74.0.apkm
"""

import sys
import os
import zipfile
import re

LEAVE_FRAME = bytes([0xef, 0x03, 0x1d, 0xaa, 0xfd, 0x79, 0xc1, 0xa8, 0xc0, 0x03, 0x5f, 0xd6])
ENTER_FRAME = bytes([0xfd, 0x79, 0xbf, 0xa9, 0xfd, 0x03, 0x0f, 0xaa])
LDUR_UBFX = bytes([0x01, 0xf0, 0x5f, 0xf8, 0x21, 0x7c, 0x4c, 0xd3])

def extract_libapp_bytes(file_path):
    """Extracts bytes of libapp.so from a .so, .apk, or .apkm file."""
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"Arquivo não encontrado: {file_path}")

    if file_path.endswith('.so'):
        with open(file_path, 'rb') as f:
            return f.read(), os.path.basename(file_path)

    with zipfile.ZipFile(file_path, 'r') as z:
        for name in z.namelist():
            if name.endswith('lib/arm64-v8a/libapp.so') or name == 'libapp.so':
                print(f"[+] Extraindo libapp.so de {file_path} ({name})...")
                return z.read(name), name

        for name in z.namelist():
            if 'arm64' in name and name.endswith('.apk'):
                print(f"[+] Encontrado split APK ARM64: {name}")
                split_bytes = z.read(name)
                import io
                with zipfile.ZipFile(io.BytesIO(split_bytes)) as sz:
                    for sname in sz.namelist():
                        if sname.endswith('lib/arm64-v8a/libapp.so') or sname.endswith('libapp.so'):
                            print(f"[+] Extraindo libapp.so de {name}...")
                            return sz.read(sname), sname

    raise ValueError(f"Não foi possível encontrar lib/arm64-v8a/libapp.so dentro de {file_path}")

def format_kotlin_byte_array(b_array, indent="            "):
    lines = []
    chunk_size = 4
    for i in range(0, len(b_array), chunk_size):
        chunk = b_array[i:i+chunk_size]
        formatted = ", ".join([f"0x{b:02x}.toByte()" for b in chunk])
        lines.append(f"{indent}{formatted},")
    return "\n".join(lines)

def has_yearly_pool_load(libapp_data, fn_pos):
    """Checks if function body has the pool load for 'yearly' followed by LeaveFrame."""
    scan_end = min(fn_pos + 512, len(libapp_data) - 20)
    for i in range(fn_pos + 40, scan_end, 4):
        a0, a1, a2, a3 = libapp_data[i:i+4]
        # add xRd, x27, #N, lsl #12
        if a3 == 0x91 and (a2 & 0x40) != 0 and (a1 & 0x03) == 0x03 and ((a0 >> 5) & 0x07) == 0x03:
            add_rd = a0 & 0x1F
            b0, b1, b2, b3 = libapp_data[i+4:i+8]
            # ldr x0, [xRn, #N]
            if b3 == 0xf9 and (b0 & 0x1F) == 0:
                ldr_rn = ((b0 >> 5) & 0x07) | ((b1 & 0x03) << 3)
                if ldr_rn == add_rd:
                    for j in range(7):
                        ri = i + 8 + j * 4
                        if libapp_data[ri:ri+len(LEAVE_FRAME)] == LEAVE_FRAME:
                            return True
    return False

def find_signatures(libapp_data, version_label="NOVA_VERSAO"):
    print(f"[*] Analisando binário libapp.so (tamanho: {len(libapp_data):,} bytes)...")

    # 1. Procurar candidatos de getUserSubscriptionState()
    # EnterFrame + sub x15, x15, #0x18 + ldur/ubfx class-tag
    pat_get_state = ENTER_FRAME + bytes([0xef, 0x61, 0x00, 0xd1])
    idx = 0
    candidates = []
    while True:
        pos = libapp_data.find(pat_get_state, idx)
        if pos == -1:
            break
        if libapp_data[pos:pos+48].find(LDUR_UBFX) != -1:
            if has_yearly_pool_load(libapp_data, pos):
                candidates.append(pos)
        idx = pos + 4

    print(f"[*] Candidatos com retorno 'yearly' identificados: {[hex(c) for c in candidates]}")

    get_state_pos = None
    is_sub_pos = None

    for cand in candidates:
        # Procurar isUserSubscribed nas proximidades (+/- 400 bytes)
        # EnterFrame + sub x15, x15, #0x20 e >= 5 retornos (cadeia OR)
        pat_is_sub = ENTER_FRAME + bytes([0xef, 0x81, 0x00, 0xd1])
        for off in range(max(0, cand - 400), min(len(libapp_data) - 20, cand + 500), 4):
            if libapp_data[off:off+12] == pat_is_sub:
                leaves = 0
                for j in range(off, min(off + 450, len(libapp_data)), 4):
                    if libapp_data[j:j+len(LEAVE_FRAME)] == LEAVE_FRAME:
                        leaves += 1
                if leaves >= 5:
                    get_state_pos = cand
                    is_sub_pos = off
                    print(f"[+] Par de funções identificado com sucesso!")
                    print(f"    - getUserSubscriptionState() em: {hex(get_state_pos)}")
                    print(f"    - isUserSubscribed() em:         {hex(is_sub_pos)} ({leaves} retornos)")
                    break
        if get_state_pos is not None:
            break

    if get_state_pos is None or is_sub_pos is None:
        raise RuntimeError("Não foi possível identificar o par de funções de assinatura do Ilqfk.")

    # Extrair bytes exatos
    is_sub_sig_bytes = libapp_data[is_sub_pos : is_sub_pos + 60]
    get_state_sig_bytes = libapp_data[get_state_pos : get_state_pos + 44]

    kotlin_code = f"""    // Ilqfk {version_label} (lib/arm64-v8a/libapp.so)
    VersionSignatures(
        version = "{version_label}",
        isUserSubscribedSig = byteArrayOf(
{format_kotlin_byte_array(is_sub_sig_bytes)}
        ),
        getStateSig = byteArrayOf(
{format_kotlin_byte_array(get_state_sig_bytes)}
        ),
    ),"""

    print("\n" + "=" * 80)
    print(f"CÓDIGO GERADO COM SUCESSO PARA A VERSÃO {version_label}!")
    print("=" * 80)
    print(kotlin_code)
    print("=" * 80)
    print("\nPróximos passos:")
    print(f"1. Copie o bloco acima e cole na lista `versionSignatures` em `UnlockPlusPatch.kt`.")
    print(f"2. Adicione \"{version_label}\" na lista `FINCH_VERSIONS` em `UnlockPlusPatch.kt`.")
    print(f"3. Atualize o `patches-list.json` e `README.md` (ou use git commit & push para o release automático).\n")

def main():
    if len(sys.argv) < 2:
        print("Uso: python tools/find_ilqfk_signatures.py <arquivo.apk|arquivo.apkm|libapp.so> [versao]")
        sys.exit(1)

    file_path = sys.argv[1]
    version = sys.argv[2] if len(sys.argv) > 2 else "NOVA_VERSAO"

    if version == "NOVA_VERSAO":
        m = re.search(r'3\.\d+\.\d+', file_path)
        if m:
            version = m.group(0)

    try:
        data, name = extract_libapp_bytes(file_path)
        find_signatures(data, version)
    except Exception as e:
        print(f"\n[-] Erro: {e}")
        sys.exit(1)

if __name__ == '__main__':
    main()
