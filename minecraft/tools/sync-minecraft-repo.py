"""Copies the Minecraft side of faith-runner into its own repository folder (faith-runner-minecraft).
Run again to refresh it; only the listed files are copied, nothing is deleted outside them."""
import os
import shutil

SRC = r'C:\Users\myste\Downloads\faith-runner\faith-runner'
DST = r'C:\Users\myste\Downloads\faith-runner\faith-runner-minecraft'

# Never copied: builds, Gradle's caches, the dev client's game folder, the prologue map's code.
SKIP_DIRS = {'target', 'build', '.gradle', 'run', '.git'}
SKIP_FILES = {
    # me_assets: the prologue map (not public)
    'level.rs', 'collision.rs', 'staticmesh.rs', 'postfx.rs', 'material.rs', 'level_tests.rs',
}
SKIP_SUFFIX = ('.log', '.dll')

TREES = [
    'crates/faith_move',
    'crates/faith_anim',
    'crates/me_assets',
    'crates/faith_ffi',
    'minecraft',
]


def copy_tree(rel):
    src_root = os.path.join(SRC, rel)
    for dirpath, dirnames, filenames in os.walk(src_root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for f in filenames:
            if f in SKIP_FILES or f.endswith(SKIP_SUFFIX):
                continue
            src = os.path.join(dirpath, f)
            dst = os.path.join(DST, os.path.relpath(src, SRC))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)


for t in TREES:
    copy_tree(t)
print('copied')
