"""Render the legacy client's existing web link into a normal system-upgrade path."""
import argparse
import html
import json
from pathlib import Path
from urllib.parse import urlsplit


def render(release, origin, candidate=True):
    url = urlsplit(release['url'])
    approved = urlsplit(origin)
    if (approved.scheme != 'https' or approved.username or approved.password or not approved.netloc
            or approved.path not in ('', '/') or approved.query or approved.fragment):
        raise ValueError('approved origin must be plain HTTPS')
    if (url.scheme, url.netloc) != ('https', approved.netloc) or url.username or url.password or url.fragment:
        raise ValueError('APK must use the approved HTTPS origin')
    if release['package'] != 'site.zhangyue.store' or type(release['versionCode']) is not int or release['versionCode'] <= 8:
        raise ValueError('a higher version of the same store package is required')
    if (len(release['sha256']) != 64 or any(c not in '0123456789abcdef' for c in release['sha256'])
            or type(release['bytes']) is not int or release['bytes'] <= 0):
        raise ValueError('valid artifact identity required')
    label = '候选 · 仅供授权验收' if candidate else '正式版'
    esc = lambda value: html.escape(str(value), quote=True)
    return f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex">
<title>乐天商店兼容升级</title><style>body{{font:17px/1.7 system-ui;max-width:720px;margin:40px auto;padding:0 22px;color:#20252c;background:#f5f7fa}}main{{padding:26px;background:white;border-radius:18px}}a{{color:#175bcc}}code{{overflow-wrap:anywhere}}.download{{display:inline-block;padding:12px 20px;background:#175bcc;color:white;border-radius:10px}}</style>
<main><h1>乐天商店兼容升级</h1><p>{esc(release['versionName'])} / {release['versionCode']} · {label}</p>
<p>旧3.0.0如果点“安装”没有反应，请通过此入口下载，再交给手机系统覆盖更新。</p>
<ol><li>先保留当前应用，完成数据备份。</li><li>下载完成后，从浏览器下载记录打开APK，按系统要求操作。</li><li>系统应显示“更新”；如果要求先卸载，立即停止。不要卸载或清理原应用数据。</li></ol>
<p><a class="download" href="{esc(release['url'])}">下载并由系统更新</a></p>
<p>系统安全检查保持开启。若提示风险或阻止安装，请停止并反馈，不关闭保护，不选择绕过风险安装。</p>
<details><summary>安装包核对信息</summary><p>包名：<code>{esc(release['package'])}</code><br>大小：{release['bytes']}字节<br>SHA-256：<code>{release['sha256']}</code></p></details>
<p>此页面不会读取、导入或删除你的聊天、账号和配置。</p></main></html>'''


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--release', required=True)
    p.add_argument('--origin', required=True)
    p.add_argument('--output', required=True)
    p.add_argument('--stable', action='store_true')
    args = p.parse_args()
    Path(args.output).write_text(render(json.loads(Path(args.release).read_text()), args.origin, not args.stable))
