#!/usr/bin/env python3
"""
Automated all-in-one workflow to update the Ilqfk (Finch) patch to a new version.

This script performs the entire update in one shot:
  1. Checks APKPure for the latest version and downloads it automatically
     (or uses a local .xapk, .apkm, or .apk file in ~/Downloads / explicit path)
  2. Extracts ARM64 native signatures from lib/arm64-v8a/libapp.so
  3. Updates UnlockPlusPatch.kt (ILQFK_VERSIONS + versionSignatures)
  4. Updates patches-list.json (targets)
  5. Updates README.md (supported versions table)
  6. Commits and pushes -> triggers the Release workflow on GitHub

Usage:
    python tools/update_ilqfk.py                       # auto-check APKPure & download if newer
    python tools/update_ilqfk.py path/to/app.xapk      # explicit path (skips download)
    python tools/update_ilqfk.py --print-only          # only extract & display Kotlin code (no git/file edits)
    python tools/update_ilqfk.py --no-download         # use local files in ~/Downloads only
    python tools/update_ilqfk.py --force               # process even if version matches current
    python tools/update_ilqfk.py --no-push             # commit without pushing
    python tools/update_ilqfk.py --no-commit           # only edit files, skip git
"""

import sys
import os
import re
import io
import json
import glob
import time
import zipfile
import subprocess
import argparse
import urllib.request

if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# ─── Constants ────────────────────────────────────────────────────────────────

LEAVE_FRAME = bytes([0xef, 0x03, 0x1d, 0xaa, 0xfd, 0x79, 0xc1, 0xa8, 0xc0, 0x03, 0x5f, 0xd6])
ENTER_FRAME = bytes([0xfd, 0x79, 0xbf, 0xa9, 0xfd, 0x03, 0x0f, 0xaa])
LDUR_UBFX = bytes([0x01, 0xf0, 0x5f, 0xf8, 0x21, 0x7c, 0x4c, 0xd3])

# ─── Paths ────────────────────────────────────────────────────────────────────

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATCH_KT = os.path.join(
    REPO_ROOT, "patches", "src", "main", "kotlin",
    "g0lxs", "morphe", "patches", "ilqfk", "plus", "UnlockPlusPatch.kt",
)
PATCHES_LIST_JSON = os.path.join(REPO_ROOT, "patches-list.json")
README_MD = os.path.join(REPO_ROOT, "README.md")


# ─── Helpers ──────────────────────────────────────────────────────────────────

def die(msg):
    print(f"\n[-] Erro: {msg}", file=sys.stderr)
    sys.exit(1)


def read_file(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def write_file(path, content):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def run_git(*args):
    result = subprocess.run(
        ["git"] + list(args),
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        die(f"git {' '.join(args)} falhou:\n{result.stderr.strip()}")
    return result.stdout.strip()


def relpath(path):
    return os.path.relpath(path, REPO_ROOT)


def _version_from_filename(filepath):
    m = re.search(r"(\d+\.\d+\.\d+)", os.path.basename(filepath))
    if not m:
        die(f"Não foi possível extrair a versão do nome do arquivo: {os.path.basename(filepath)}")
    return m.group(1)


# ═══════════════════════════════════════════════════════════════════════════════
#  Step 1 — APKPure Downloader & Package Detection
# ═══════════════════════════════════════════════════════════════════════════════

def get_latest_apkpure_info(package_id="com.finch.finch"):
    """
    Queries APKPure to discover the latest version and direct download link.
    Returns (version, download_url) or (None, None) if unreachable.
    """
    url = f"https://d.apkpure.com/b/XAPK/{package_id}?version=latest"
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/130.0.0.0 Safari/537.36"
        )
    }

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None

    opener = urllib.request.build_opener(NoRedirect)
    req = urllib.request.Request(url, headers=headers)
    try:
        resp = opener.open(req, timeout=15)
        loc = resp.headers.get("Location")
    except urllib.error.HTTPError as e:
        loc = e.headers.get("Location")
    except Exception as e:
        print(f"[!] Falha ao consultar APKPure: {e}")
        return None, None

    if not loc:
        return None, None

    m = re.search(r"(\d+\.\d+\.\d+)", loc)
    version = m.group(1) if m else None
    return version, loc


def download_from_apkpure(download_url, version, dest_dir=None, package_id="com.finch.finch"):
    """
    Downloads the XAPK package from APKPure directly into dest_dir with a progress indicator.
    Returns the path to the downloaded file.
    """
    if dest_dir is None:
        dest_dir = os.path.join(os.path.expanduser("~"), "Downloads")

    os.makedirs(dest_dir, exist_ok=True)
    filename = f"{package_id}_{version}.xapk"
    dest_path = os.path.join(dest_dir, filename)

    if os.path.exists(dest_path) and os.path.getsize(dest_path) > 10 * 1024 * 1024:
        print(f"[*] Pacote já existe localmente: {dest_path} ({os.path.getsize(dest_path) / 1024 / 1024:.1f} MB)")
        return dest_path

    print(f"[*] Baixando {package_id} {version} do APKPure...")
    print(f"    Destino: {dest_path}")
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/130.0.0.0 Safari/537.36"
        )
    }

    req = urllib.request.Request(download_url, headers=headers)
    temp_path = dest_path + ".part"

    try:
        with urllib.request.urlopen(req, timeout=30) as resp, open(temp_path, "wb") as f:
            total_size = int(resp.headers.get("Content-Length", 0))
            downloaded = 0
            t0 = time.time()
            last_print = 0
            while True:
                chunk = resp.read(512 * 1024)
                if not chunk:
                    break
                f.write(chunk)
                downloaded += len(chunk)
                now = time.time()
                if now - last_print > 0.5 or (total_size and downloaded == total_size):
                    speed = downloaded / (now - t0 + 1e-6) / (1024 * 1024)
                    pct = (downloaded / total_size * 100) if total_size else 0
                    mb = downloaded / (1024 * 1024)
                    total_mb = total_size / (1024 * 1024) if total_size else 0
                    if total_size:
                        print(f"    [{pct:5.1f}%] {mb:6.1f} / {total_mb:6.1f} MB ({speed:4.1f} MB/s)", end="\r", flush=True)
                    else:
                        print(f"    {mb:6.1f} MB baixados ({speed:4.1f} MB/s)", end="\r", flush=True)
                    last_print = now

        if os.path.exists(dest_path):
            os.remove(dest_path)
        os.replace(temp_path, dest_path)
        print(f"\n[+] Download concluído com sucesso!")
        return dest_path
    except Exception as e:
        if os.path.exists(temp_path):
            os.remove(temp_path)
        die(f"Erro durante o download do APKPure: {e}")


def find_local_package(explicit_path=None):
    """Locate an existing Finch APK, APKM, or XAPK file. Returns (path, version)."""
    if explicit_path:
        if not os.path.isfile(explicit_path):
            die(f"Arquivo não encontrado: {explicit_path}")
        return explicit_path, _version_from_filename(explicit_path)

    downloads = os.path.join(os.path.expanduser("~"), "Downloads")
    patterns = [
        "com.finch.finch_*.xapk",
        "com.finch.finch_*.apkm",
        "com.finch.finch_*.apk",
        "*finch*.xapk",
        "*finch*.apkm",
        "*finch*.apk",
    ]
    matches = []
    for pat in patterns:
        for p in glob.glob(os.path.join(downloads, pat)):
            if p not in matches and not p.endswith(".part"):
                matches.append(p)

    matches.sort(key=os.path.getmtime, reverse=True)
    if not matches:
        return None, None

    path = matches[0]
    if len(matches) > 1:
        print(f"[*] Múltiplos pacotes encontrados, usando o mais recente: {os.path.basename(path)}")
    return path, _version_from_filename(path)


# ═══════════════════════════════════════════════════════════════════════════════
#  Step 2 — Native ARM64 Signature Extraction
# ═══════════════════════════════════════════════════════════════════════════════

def format_kotlin_byte_array(b_array, indent="            "):
    lines = []
    chunk_size = 4
    for i in range(0, len(b_array), chunk_size):
        chunk = b_array[i : i + chunk_size]
        formatted = ", ".join([f"0x{b:02x}.toByte()" for b in chunk])
        lines.append(f"{indent}{formatted},")
    return "\n".join(lines)


def has_yearly_pool_load(libapp_data, fn_pos):
    """Checks if function body has the pool load for 'yearly' followed by LeaveFrame."""
    scan_end = min(fn_pos + 512, len(libapp_data) - 20)
    for i in range(fn_pos + 40, scan_end, 4):
        a0, a1, a2, a3 = libapp_data[i : i + 4]
        # add xRd, x27, #N, lsl #12
        if a3 == 0x91 and (a2 & 0x40) != 0 and (a1 & 0x03) == 0x03 and ((a0 >> 5) & 0x07) == 0x03:
            add_rd = a0 & 0x1F
            b0, b1, b2, b3 = libapp_data[i + 4 : i + 8]
            # ldr x0, [xRn, #N]
            if b3 == 0xf9 and (b0 & 0x1F) == 0:
                ldr_rn = ((b0 >> 5) & 0x07) | ((b1 & 0x03) << 3)
                if ldr_rn == add_rd:
                    for j in range(7):
                        ri = i + 8 + j * 4
                        if libapp_data[ri : ri + len(LEAVE_FRAME)] == LEAVE_FRAME:
                            return True
    return False


def extract_libapp_bytes(file_path):
    """Extracts bytes of libapp.so from a .so, .apk, .apkm, or .xapk file."""
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"Arquivo não encontrado: {file_path}")

    if file_path.endswith(".so"):
        with open(file_path, "rb") as f:
            return f.read(), os.path.basename(file_path)

    with zipfile.ZipFile(file_path, "r") as z:
        for name in z.namelist():
            if name.endswith("lib/arm64-v8a/libapp.so") or name == "libapp.so":
                print(f"[+] Extraindo libapp.so de {file_path} ({name})...")
                return z.read(name), name

        for name in z.namelist():
            if "arm64" in name and name.endswith(".apk"):
                print(f"[+] Encontrado split APK ARM64: {name}")
                split_bytes = z.read(name)
                with zipfile.ZipFile(io.BytesIO(split_bytes)) as sz:
                    for sname in sz.namelist():
                        if sname.endswith("lib/arm64-v8a/libapp.so") or sname.endswith("libapp.so"):
                            print(f"[+] Extraindo libapp.so de {name}...")
                            return sz.read(sname), sname

        # Fallback: scan any APK inside the bundle/zip for arm64 libapp.so
        for name in z.namelist():
            if name.endswith(".apk") and "arm64" not in name:
                split_bytes = z.read(name)
                with zipfile.ZipFile(io.BytesIO(split_bytes)) as sz:
                    for sname in sz.namelist():
                        if sname.endswith("lib/arm64-v8a/libapp.so") or sname.endswith("libapp.so"):
                            print(f"[+] Extraindo libapp.so de {name} ({sname})...")
                            return sz.read(sname), sname

    raise ValueError(f"Não foi possível encontrar lib/arm64-v8a/libapp.so dentro de {file_path}")


def extract_signatures(libapp_data):
    """
    Find isUserSubscribed() and getUserSubscriptionState() in libapp.so.
    Returns (is_sub_sig_bytes, get_state_sig_bytes).
    """
    print(f"[*] Analisando libapp.so ({len(libapp_data):,} bytes)...")

    # ── getUserSubscriptionState candidates ──
    # Pattern: EnterFrame + sub x15, x15, #0x18
    pat_get_state = ENTER_FRAME + bytes([0xef, 0x61, 0x00, 0xd1])
    idx = 0
    candidates = []
    while True:
        pos = libapp_data.find(pat_get_state, idx)
        if pos == -1:
            break
        if libapp_data[pos : pos + 48].find(LDUR_UBFX) != -1:
            if has_yearly_pool_load(libapp_data, pos):
                candidates.append(pos)
        idx = pos + 4

    print(f"[*] Candidatos getUserSubscriptionState: {[hex(c) for c in candidates]}")

    # ── isUserSubscribed paired with each candidate ──
    # Pattern: EnterFrame + sub x15, x15, #0x20
    pat_is_sub = ENTER_FRAME + bytes([0xef, 0x81, 0x00, 0xd1])
    get_state_pos = None
    is_sub_pos = None

    for cand in candidates:
        scan_lo = max(0, cand - 400)
        scan_hi = min(len(libapp_data) - 20, cand + 500)
        for off in range(scan_lo, scan_hi, 4):
            if libapp_data[off : off + 12] == pat_is_sub:
                leaves = sum(
                    1
                    for j in range(off, min(off + 450, len(libapp_data)), 4)
                    if libapp_data[j : j + len(LEAVE_FRAME)] == LEAVE_FRAME
                )
                if leaves >= 5:
                    get_state_pos = cand
                    is_sub_pos = off
                    print(f"[+] Par de funções identificado!")
                    print(f"    getUserSubscriptionState() em: {hex(get_state_pos)}")
                    print(f"    isUserSubscribed()         em: {hex(is_sub_pos)} ({leaves} retornos)")
                    break
        if get_state_pos is not None:
            break

    if get_state_pos is None or is_sub_pos is None:
        die("Não foi possível localizar o par de funções. O arquivo pode ser incompatível.")

    is_sub_sig = libapp_data[is_sub_pos : is_sub_pos + 60]
    get_state_sig = libapp_data[get_state_pos : get_state_pos + 44]

    # Verify uniqueness
    for label, sig in [("isUserSubscribed", is_sub_sig), ("getUserSubscriptionState", get_state_sig)]:
        count = 0
        i = 0
        while True:
            pos = libapp_data.find(sig, i)
            if pos == -1:
                break
            count += 1
            i = pos + 1
        if count != 1:
            die(f"Assinatura de {label} não é única ({count} ocorrências). Assinatura muito fraca.")

    print("[+] Assinaturas únicas verificadas ✓")
    return is_sub_sig, get_state_sig


def format_kotlin_block(version, is_sub_sig, get_state_sig):
    return (
        f"    // Ilqfk {version} (lib/arm64-v8a/libapp.so)\n"
        f"    VersionSignatures(\n"
        f"        version = \"{version}\",\n"
        f"        isUserSubscribedSig = byteArrayOf(\n"
        f"{format_kotlin_byte_array(is_sub_sig)}\n"
        f"        ),\n"
        f"        getStateSig = byteArrayOf(\n"
        f"{format_kotlin_byte_array(get_state_sig)}\n"
        f"        ),\n"
        f"    ),"
    )


# ═══════════════════════════════════════════════════════════════════════════════
#  Step 3 — Update UnlockPlusPatch.kt
# ═══════════════════════════════════════════════════════════════════════════════

def update_kotlin_file(version, is_sub_sig, get_state_sig):
    content = read_file(PATCH_KT)

    # 3a. ILQFK_VERSIONS
    content = re.sub(
        r'private val ILQFK_VERSIONS = listOf\([^)]*\)',
        f'private val ILQFK_VERSIONS = listOf("{version}")',
        content,
    )

    # 3b. versionSignatures block — find the balanced parentheses
    marker = "private val versionSignatures = listOf("
    start = content.find(marker)
    if start == -1:
        die(f"Não encontrou '{marker}' em UnlockPlusPatch.kt")

    depth = 0
    i = start + len(marker) - 1  # the '(' character
    end = None
    while i < len(content):
        if content[i] == "(":
            depth += 1
        elif content[i] == ")":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
        i += 1
    if end is None:
        die("Não encontrou o ')' de fechamento de versionSignatures.")

    new_block = (
        f"private val versionSignatures = listOf(\n"
        f"{format_kotlin_block(version, is_sub_sig, get_state_sig)}\n"
        f")"
    )

    content = content[:start] + new_block + content[end:]
    write_file(PATCH_KT, content)
    print(f"[+] Atualizado {relpath(PATCH_KT)}")


# ═══════════════════════════════════════════════════════════════════════════════
#  Step 4 — Update patches-list.json
# ═══════════════════════════════════════════════════════════════════════════════

def update_patches_list(version):
    data = json.loads(read_file(PATCHES_LIST_JSON))

    for patch in data.get("patches", []):
        for pkg in patch.get("compatiblePackages", []):
            if pkg.get("packageName") == "com.finch.finch":
                pkg["targets"] = [
                    {
                        "version": version,
                        "isExperimental": False,
                        "minSdk": None,
                        "description": None,
                    }
                ]

    write_file(PATCHES_LIST_JSON, json.dumps(data, indent=2, ensure_ascii=False) + "\n")
    print(f"[+] Atualizado {relpath(PATCHES_LIST_JSON)}")


# ═══════════════════════════════════════════════════════════════════════════════
#  Step 5 — Update README.md
# ═══════════════════════════════════════════════════════════════════════════════

def update_readme(version):
    content = read_file(README_MD)

    # Replace the version table anchored by "Supported versions:"
    content = re.sub(
        r"(\*\*🎯 Supported versions:\*\*\n\n)\|[^\n]+\|\n\|[^\n]+\|",
        rf"\g<1>| {version} |\n| :---: |",
        content,
    )

    write_file(README_MD, content)
    print(f"[+] Atualizado {relpath(README_MD)}")


# ═══════════════════════════════════════════════════════════════════════════════
#  Step 6 — Git commit & push
# ═══════════════════════════════════════════════════════════════════════════════

def git_commit_and_push(version, push=True):
    run_git("add", PATCH_KT, PATCHES_LIST_JSON, README_MD)

    # Check if there are actual staged changes
    result = subprocess.run(
        ["git", "diff", "--cached", "--quiet"],
        cwd=REPO_ROOT,
        capture_output=True,
    )
    if result.returncode == 0:
        print("[!] Nenhuma alteração para commitar (versão já pode estar atualizada).")
        return

    run_git("commit", "-m", f"feat: support Ilqfk {version}")
    print(f"[+] Commitado: feat: support Ilqfk {version}")

    if push:
        run_git("push", "-u", "origin", "HEAD")
        print("[+] Enviado para o origin. O workflow de Release irá compilar e publicar automaticamente.")
    else:
        print("[*] Push não realizado (--no-push). Execute 'git push' quando estiver pronto.")


# ═══════════════════════════════════════════════════════════════════════════════
#  Main
# ═══════════════════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description="Atualiza o patch Ilqfk (Finch) para uma nova versão.",
    )
    parser.add_argument(
        "package_file",
        nargs="?",
        default=None,
        help="Caminho para o APK/APKM/XAPK do Finch (baixa do APKPure ou auto-detecta em ~/Downloads se omitido)",
    )
    parser.add_argument(
        "--print-only",
        action="store_true",
        help="Apenas extrair e exibir o código Kotlin das assinaturas no terminal (sem alterar arquivos ou git)",
    )
    parser.add_argument(
        "--no-download",
        action="store_true",
        help="Não baixar do APKPure; usar apenas arquivos locais em ~/Downloads",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Forçar atualização mesmo que a versão já seja a suportada atualmente",
    )
    parser.add_argument(
        "--no-push",
        action="store_true",
        help="Commitar sem fazer push para o remote",
    )
    parser.add_argument(
        "--no-commit",
        action="store_true",
        help="Só editar os arquivos, sem git commit/push",
    )
    args = parser.parse_args()

    print("=" * 60)
    print("  Ilqfk (Finch) - Patch Updater")
    print("=" * 60)

    # Current version in repo
    current_content = read_file(PATCH_KT)
    m = re.search(r'ILQFK_VERSIONS = listOf\("([^"]+)"', current_content)
    current_ver = m.group(1) if m else None

    package_path = None
    version = None

    # ── 1. Obtain Package (APKPure download or local detection) ──
    if args.package_file:
        if not os.path.isfile(args.package_file):
            die(f"Arquivo não encontrado: {args.package_file}")
        package_path = args.package_file
        version = _version_from_filename(package_path)
    elif not args.no_download:
        print(f"\n[1/6] Verificando versão mais recente no APKPure...")
        latest_ver, dl_url = get_latest_apkpure_info()
        if latest_ver and dl_url:
            print(f"      Versão no APKPure:     {latest_ver}")
            print(f"      Versão no repositório: {current_ver}")
            if latest_ver == current_ver and not args.force and not args.print_only:
                print(f"\n[+] A versão {latest_ver} já é a versão suportada atualmente no repositório. Nada a fazer.")
                print(f"    (Dica: use --force para forçar a re-execução do processo).")
                sys.exit(0)
            package_path = download_from_apkpure(dl_url, latest_ver)
            version = latest_ver
        else:
            print("[!] Não foi possível checar o APKPure. Buscando arquivos locais...")

    if not package_path:
        package_path, version = find_local_package()
        if not package_path:
            die(
                "Nenhum arquivo do Finch (.xapk, .apkm ou .apk) encontrado em ~/Downloads,\n"
                "e não foi possível baixar do APKPure.\n"
                "Passe o caminho diretamente:\n"
                "  python tools/update_ilqfk.py caminho/para/arquivo.xapk"
            )

    print(f"\n[1/6] Pacote: {os.path.basename(package_path)}")
    print(f"      Versão: {version}")

    if current_ver == version and not args.force and not args.print_only:
        die(f"A versão {version} já é a versão atual. Nada a fazer (use --force para forçar).")
    elif current_ver != version:
        print(f"      Atualização: {current_ver} → {version}")

    # ── 2. Extract signatures ──
    print(f"\n[2/6] Extraindo assinaturas do ARM64...")
    libapp_data, _ = extract_libapp_bytes(package_path)
    is_sub_sig, get_state_sig = extract_signatures(libapp_data)

    # ── Inspect / Print Only Mode ──
    if args.print_only:
        print("\n" + "=" * 60)
        print(f"  CÓDIGO GERADO PARA A VERSÃO {version}:")
        print("=" * 60)
        print(format_kotlin_block(version, is_sub_sig, get_state_sig))
        print("=" * 60)
        print("\n[+] Modo --print-only finalizado sem alterar arquivos.")
        return

    # ── 3. Update Kotlin ──
    print(f"\n[3/6] Atualizando UnlockPlusPatch.kt...")
    update_kotlin_file(version, is_sub_sig, get_state_sig)

    # ── 4. Update patches-list.json ──
    print(f"\n[4/6] Atualizando patches-list.json...")
    update_patches_list(version)

    # ── 5. Update README.md ──
    print(f"\n[5/6] Atualizando README.md...")
    update_readme(version)

    # ── 6. Commit & push ──
    if args.no_commit:
        print(f"\n[6/6] Passo de git pulado (--no-commit).")
    else:
        print(f"\n[6/6] Commitando e enviando...")
        git_commit_and_push(version, push=not args.no_push)

    print("\n" + "=" * 60)
    print(f"  [+] Ilqfk atualizado para {version} com sucesso!")
    print("=" * 60)


if __name__ == "__main__":
    main()
