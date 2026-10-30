"""
Takem Titans - builds the release packages for the current version from the previous ones in release/.

Takes mod_version from gradle.properties and build/libs/takemtitans-<version>.jar (run `gradlew build` first), copies
every entry of the previous Modrinth pack, CurseForge zip and installer zip, swaps in the new jar and version, and
removes the previous version's files from release/.

Run from the repo root:  python tools/package_release.py
"""
import glob
import os
import re
import shutil
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REL = os.path.join(REPO, 'release')


def version():
    for line in open(os.path.join(REPO, 'gradle.properties'), encoding='utf-8'):
        if line.startswith('mod_version='):
            return line.split('=', 1)[1].strip()
    raise SystemExit('no mod_version in gradle.properties')


def rewrite(src, dst, old, new, jar):
    jar_name = 'takemtitans-%s.jar' % new
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            name = info.filename
            if re.search(r'(^|/)takemtitans-[^/]*\.jar$', name):
                zout.write(jar, name.rsplit('/', 1)[0] + '/' + jar_name if '/' in name else jar_name)
                continue
            data = zin.read(name)
            if name in ('modrinth.index.json', 'manifest.json') or name.endswith('.bat'):
                data = data.replace(old.encode(), new.encode())
            zout.writestr(info, data)


def main():
    new = version()
    jar = os.path.join(REPO, 'build/libs/takemtitans-%s.jar' % new)
    if not os.path.exists(jar):
        raise SystemExit('build first: %s is missing' % jar)
    packs = glob.glob(os.path.join(REL, 'TakemTitans-*.mrpack'))
    old = None
    for p in packs:
        m = re.search(r'TakemTitans-(.+)\.mrpack$', p)
        if m and m.group(1) != new:
            old = m.group(1)
    if old is None:
        raise SystemExit('no previous release in release/ to build from')
    for suffix in ('.mrpack', '-CurseForge.zip', '-Installer.zip'):
        src = os.path.join(REL, 'TakemTitans-%s%s' % (old, suffix))
        dst = os.path.join(REL, 'TakemTitans-%s%s' % (new, suffix))
        rewrite(src, dst, old, new, jar)
        os.remove(src)
        print('  %s' % os.path.basename(dst))
    shutil.copyfile(jar, os.path.join(REL, 'takemtitans-%s.jar' % new))
    old_jar = os.path.join(REL, 'takemtitans-%s.jar' % old)
    if os.path.exists(old_jar):
        os.remove(old_jar)
    print('  takemtitans-%s.jar  (was %s)' % (new, old))


if __name__ == '__main__':
    main()
