from pathlib import Path
from urllib.request import urlopen
root=Path('app/src/main/res/font')
root.mkdir(exist_ok=True)
url='https://raw.githubusercontent.com/google/fonts/main/ofl/inter/'
(root/'inter_variable.ttf').write_bytes(urlopen(url+'Inter%5Bopsz%2Cwght%5D.ttf').read())
license=Path('app/src/main/assets/licenses')
license.mkdir(parents=True,exist_ok=True)
(license/'Inter-OFL.txt').write_bytes(urlopen(url+'OFL.txt').read())
