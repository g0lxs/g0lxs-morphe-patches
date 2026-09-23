#!/usr/bin/env python3
"""
Automated workflow to update the Ilqfk (Ilqfk) patch to a new version.

This script performs the entire update in one shot:
  1. Finds the Ilqfk APKM in ~/Downloads (or uses a provided path)
  2. Extracts ARM64 native signatures from lib/arm64-v8a/libapp.so
  3. Updates UnlockPlusPatch.kt (ILQFK_VERSIONS + versionSignatures)
  4. Updates patches-list.json (targets)
  5. Updates README.md (supported versions table)
  6. Commits and pushes → triggers the Release workflow on GitHub

Usage:
    python tools/update_ilqfk.py                       # auto-detect APKM in Downloads
    python tools/update_ilqfk.py path/to/ilqfk.apkm    # explicit path
    python tools/update_ilqfk.py --no-push              # commit without pushing
    python tools/update_ilqfk.py --no-commit            # only edit files, skip git
"""

import sys
import os
import re
import json
import glob
import subprocess
import argparse

# ─── Import signature-extraction utilities from the companion tool ────────────

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from find_ilqfk_signatures import (
    extract_libapp_bytes,
    format_kotlin_byte_array,
    LEAVE_FRAME,
    ENTER_FRAME,
    LDUR_UBFX,
    has_yearly_pool_load,
)

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


# ═══════════════════════════════════════════════════════════════════════════════
#  Step 1 — Find the APKM
# ═══════════════════════════════════════════════════════════════════════════════

def find_apkm(explicit_path=None):
    """Locate the Ilqfk APKM file.  Returns (path, version)."""
    if explicit_path:
        if not os.path.isfile(explicit_path):
            die(f"Arquivo não encontrado: {explicit_path}")
        return explicit_path, _version_from_filename(explicit_path)

    downloads = os.path.join(os.path.expanduser("~"), "Downloads")
    matches = sorted(
        glob.glob(os.path.join(downloads, "com.finch.finch_*.apkm")),
        key=os.path.getmtime,
        reverse=True,
    )
    if not matches:
        die(
            f"Nenhum APKM do Ilqfk encontrado em {downloads}.\n"
            "Baixe pelo APKMirror ou passe o caminho diretamente:\n"
            "  python tools/update_ilqfk.py caminho/para/ilqfk.apkm"
        )

    path = matches[0]
    if len(matches) > 1:
        print(f"[*] Múltiplos APKMs encontrados, usando o mais recente: {os.path.basename(path)}")
    return path, _version_from_filename(path)


def _version_from_filename(filepath):
    m = re.search(r"(\d+\.\d+\.\d+)", os.path.basename(filepath))
    if not m:
        die(f"Não foi possível extrair a versão do nome do arquivo: {os.path.basename(filepath)}")
    return m.group(1)


# ═══════════════════════════════════════════════════════════════════════════════
#  Step 2 — Extract ARM64 signatures
# ═══════════════════════════════════════════════════════════════════════════════

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
        die("Não foi possível localizar o par de funções. O APKM pode ser incompatível.")

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
        f"    // Ilqfk {version} (lib/arm64-v8a/libapp.so)\n"
        f"    VersionSignatures(\n"
        f"        version = \"{version}\",\n"
        f"        isUserSubscribedSig = byteArrayOf(\n"
        f"{format_kotlin_byte_array(is_sub_sig)}\n"
        f"        ),\n"
        f"        getStateSig = byteArrayOf(\n"
        f"{format_kotlin_byte_array(get_state_sig)}\n"
        f"        ),\n"
        f"    ),\n"
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
        description="Atualiza o patch Ilqfk (Ilqfk) para uma nova versão.",
    )
    parser.add_argument(
        "apkm",
        nargs="?",
        default=None,
        help="Caminho para o APKM do Ilqfk (auto-detecta em ~/Downloads se omitido)",
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
    print("  Ilqfk (Ilqfk) — Patch Updater")
    print("=" * 60)

    # ── 1. Find the APKM ──
    apkm_path, version = find_apkm(args.apkm)
    print(f"\n[1/6] APKM: {os.path.basename(apkm_path)}")
    print(f"      Versão: {version}")

    # Check if version is already current
    current_content = read_file(PATCH_KT)
    m = re.search(r'ILQFK_VERSIONS = listOf\("([^"]+)"', current_content)
    if m:
        current_ver = m.group(1)
        if current_ver == version:
            die(f"A versão {version} já é a versão atual. Nada a fazer.")
        print(f"      Atual:  {current_ver} → {version}")

    # ── 2. Extract signatures ──
    print(f"\n[2/6] Extraindo assinaturas do ARM64...")
    libapp_data, _ = extract_libapp_bytes(apkm_path)
    is_sub_sig, get_state_sig = extract_signatures(libapp_data)

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
    print(f"  ✅ Ilqfk atualizado para {version} com sucesso!")
    print("=" * 60)


if __name__ == "__main__":
    main()
