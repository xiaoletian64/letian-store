"""Build the read-only store state probe and synthetic version transition fixtures."""
import os
from pathlib import Path
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
sdk = Path(os.environ.get('ANDROID_HOME', '/opt/android-sdk'))
bt = sorted((sdk/'build-tools').iterdir())[-1]
platform = sorted((sdk/'platforms').glob('*/android.jar'))[-1]
props = dict(line.split('=', 1) for line in (root/'config/store.properties').read_text().splitlines()
             if '=' in line and not line.startswith('#'))
os.environ['STORE_PROBE_PASSWORD'] = props['storepass']
os.environ['STORE_PROBE_KEY_PASSWORD'] = props['keypass']

def run(*cmd):
    subprocess.run([str(x) for x in cmd], check=True)

for version in (-3, -2, -1, 0, 1, 2, 3, 4):
    out = root/'.build-probes'/str(version)
    out.mkdir(parents=True, exist_ok=True)
    if version == -3:
        package = 'site.zhangyue.store.legacylinkprobe'
        body = '<application android:label="旧版升级入口验证（隔离）"/><instrumentation android:name="test.LegacyLinkProbe" android:targetPackage="site.zhangyue.store"/>'
    elif version == -2:
        package = 'site.zhangyue.store.installprobe'
        body = '<application android:label="安装会话验证（隔离）"/><instrumentation android:name="test.InstallerProbe" android:targetPackage="site.zhangyue.store"/>'
    elif version == -1:
        package = 'site.zhangyue.store.readonlydata'
        body = '<application android:label="商店只读备份（维护工具）"/><instrumentation android:name="test.ReadOnlyDataProbe" android:targetPackage="site.zhangyue.store"/>'
    elif version == 0:
        package = 'site.zhangyue.store.stateprobe'
        body = '<application android:label="商店状态验证（隔离）"/><instrumentation android:name="test.InstalledStateProbe" android:targetPackage="site.zhangyue.store"/>'
    else:
        package = 'site.zhangyue.store.qa.fixture'
        body = '<application android:hasCode="false" android:label="QA状态夹具"><activity android:name="android.app.Activity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity></application>'
    (out/'AndroidManifest.xml').write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}" android:versionCode="{max(1,version)}" android:versionName="{max(1,version)}"><uses-sdk android:minSdkVersion="24" android:targetSdkVersion="35"/>{body}</manifest>')
    run(bt/'aapt','package','-f','-M',out/'AndroidManifest.xml','-I',platform,'-F',out/'unsigned.apk')
    if version <= 0:
        for name in ('classes','dex'): (out/name).mkdir(exist_ok=True)
        run('javac','-encoding','UTF-8','-source','8','-target','8','-cp',platform,'-d',out/'classes',root/('tests/LegacyLinkProbe.java' if version == -3 else 'tests/InstallerProbe.java' if version == -2 else 'tests/ReadOnlyDataProbe.java' if version == -1 else 'tests/InstalledStateProbe.java'))
        run('jar','cf',out/'probe.jar','-C',out/'classes','.')
        run(bt/'d8','--min-api','24','--lib',platform,'--output',out/'dex',out/'probe.jar')
        with zipfile.ZipFile(out/'unsigned.apk','a') as z: z.write(out/'dex/classes.dex','classes.dex')
    run(bt/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk')
    if version == 4:
        import secrets
        key = root/'.build-probes/fixture-only.jks'
        password = root/'.build-probes/fixture-only.password'
        if not key.exists():
            password.write_text(secrets.token_urlsafe(24)); password.chmod(0o600)
            os.environ['FIXTURE_ONLY_PASSWORD'] = password.read_text()
            run('keytool','-genkeypair','-keystore',key,'-alias','fixture','-storepass:env','FIXTURE_ONLY_PASSWORD',
                '-keypass:env','FIXTURE_ONLY_PASSWORD','-keyalg','RSA','-keysize','2048','-validity','2','-dname','CN=StoreFixtureOnly')
        os.environ['FIXTURE_ONLY_PASSWORD'] = password.read_text()
        signing = ['--ks',key,'--ks-key-alias','fixture','--ks-pass','env:FIXTURE_ONLY_PASSWORD','--key-pass','env:FIXTURE_ONLY_PASSWORD']
    else:
        signing = ['--ks',props['keystore'],'--ks-key-alias',props['alias'],'--ks-pass','env:STORE_PROBE_PASSWORD','--key-pass','env:STORE_PROBE_KEY_PASSWORD']
    run(bt/'apksigner','sign',*signing,'--out',out/'signed.apk',out/'aligned.apk')
print('probes and isolated fixture versions built')
