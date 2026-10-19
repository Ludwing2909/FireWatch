"""Empaqueta fuentes y artefactos, con dataset ZIP, sin entornos ni cachés."""
from pathlib import Path
import os, zipfile
ROOT=Path(__file__).resolve().parents[1]
def main():
    out=ROOT/'entrega'; out.mkdir(exist_ok=True)
    target=out/'BosqueAlerta_Proyecto.zip'
    excluded={'.venv','.tools','.git','__pycache__','.gradle','build','original','entrega'}
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=4) as z:
        for folder,dirs,files in os.walk(ROOT):
            dirs[:]=[d for d in dirs if d not in excluded]
            for name in files:
                path=Path(folder)/name
                rel=path.relative_to(ROOT)
                if path.name=='local.properties': continue
                z.write(path,Path('BosqueAlerta')/rel,compress_type=zipfile.ZIP_STORED if path.suffix.lower() in {'.zip','.apk','.keras','.png'} else zipfile.ZIP_DEFLATED)
    print(target, f'{target.stat().st_size/1024**2:.1f} MB')
if __name__=='__main__': main()
