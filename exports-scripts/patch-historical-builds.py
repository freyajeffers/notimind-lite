#!/usr/bin/env python3
import re
from pathlib import Path
import tomllib

ROOT = Path(__file__).resolve().parent.parent
TOML = ROOT / 'gradle' / 'libs.versions.toml'
if not TOML.exists():
    print('No gradle/libs.versions.toml found; abort')
    raise SystemExit(1)

cfg = tomllib.loads(TOML.read_text())
versions = cfg.get('versions', {})
libs = cfg.get('libraries', {})
plugins = cfg.get('plugins', {})

# helpers
def lib_coord(key):
    # key like 'androidx.activity.compose' -> toml key 'androidx-activity-compose'
    key = "androidx.compose.bom" if key == "android.compose.bom" else key
    toml_key = key.replace('.', '-').replace('libs.plugins.', '')
    entry = libs.get(toml_key)
    if not entry:
        return None
    group = entry.get('group')
    name = entry.get('name')
    # tomllib parses version.ref = "x" as {"version": {"ref": "x"}}.
    version = entry.get('version')
    if isinstance(version, dict):
        ver = versions.get(version.get('ref'))
    else:
        ver = version
    if not (group and name and ver):
        return None
    return f"{group}:{name}:{ver}"

def plugin_decl(toml_key):
    entry = plugins.get(toml_key)
    if not entry:
        return None
    pid = entry.get('id')
    version = entry.get('version')
    if isinstance(version, dict):
        ver = versions.get(version.get('ref'))
    else:
        ver = version
    if not pid:
        return None
    return f'id("{pid}")'

# process each worktree
worktrees = list((ROOT / '.worktrees' / 'schemas').glob('*'))
for wt in worktrees:
    app_build = wt / 'app' / 'build.gradle.kts'
    if not app_build.exists():
        continue
    text = app_build.read_text()
    orig = text
    # replace any dependency configuration using platform(libs.xxx)
    text = re.sub(
        r'([A-Za-z]+)\(\s*platform\(\s*libs\.([a-zA-Z0-9_\.]+)\s*\)\s*\)',
        lambda m: f'{m.group(1)}(platform("{lib_coord(m.group(2))}"))' if lib_coord(m.group(2)) else m.group(0),
        text,
    )
    # replace implementation(libs.xxx)
    def repl_impl(m):
        key = m.group(1)
        coord = lib_coord(key)
        if coord:
            return f'implementation("{coord}")'
        return m.group(0)
    text = re.sub(r'implementation\(\s*libs\.([a-zA-Z0-9_\.]+)\s*\)', repl_impl, text)
    # replace debugImplementation/lint etc like debugImplementation(libs.xxx)
    text = re.sub(r'([a-zA-Z]+Implementation)\(\s*libs\.([a-zA-Z0-9_\.]+)\s*\)', lambda m: f"{m.group(1)}(\"{lib_coord(m.group(2))}\")" if lib_coord(m.group(2)) else m.group(0), text)

    # plugins block replacement: find plugins { ... } and replace alias(libs.plugins.xxx) entries
    plugins_block = re.search(r'plugins\s*\{([\s\S]*?)\n\}', text)
    if plugins_block:
        body = plugins_block.group(1)
        plugin_keys = re.findall(r'alias\(libs\.plugins\.([a-zA-Z0-9_\.]+)\)', body)
        decls = []
        for pk in plugin_keys:
            toml_key = pk.replace('.', '-')
            d = plugin_decl(toml_key)
            if d:
                decls.append(d)
        if decls:
            new_block = 'plugins {\n  ' + '\n  '.join(decls) + '\n}\n'
            text = text[:plugins_block.start()] + new_block + text[plugins_block.end():]
    if text != orig:
        app_build.write_text(text)
        print(f'Patched {app_build}')
    else:
        print(f'No changes for {app_build}')
