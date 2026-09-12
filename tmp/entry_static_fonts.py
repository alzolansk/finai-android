from pathlib import Path
import re,subprocess,concurrent.futures
css=Path('tmp/inter.css').read_text()
def download(item):
    weight,url=item
    subprocess.run(['curl.exe','-L','--fail','--max-time','30',url,'-o',f'app/src/main/res/font/inter_{weight}.ttf'],check=True,capture_output=True)
with concurrent.futures.ThreadPoolExecutor() as pool:
    list(pool.map(download,re.findall(r'font-weight: (\d+);\s+src: url\(([^)]+)',css)))
folder=Path('app/src/main/assets/licenses');folder.mkdir(parents=True,exist_ok=True)
(folder/'Inter-OFL.txt').write_bytes(Path('tmp/Inter-OFL.txt').read_bytes())
