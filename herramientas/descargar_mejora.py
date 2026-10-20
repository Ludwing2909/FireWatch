"""Fuentes adicionales para revisión 2, con versión y licencia declaradas."""
from pathlib import Path
import argparse, hashlib, json, ssl, urllib.request, zipfile
ROOT=Path(__file__).resolve().parents[1]

def fetch(ref,version,slug):
    folder=ROOT/'dataset/complementario'/slug
    folder.mkdir(parents=True,exist_ok=True)
    meta=json.load(urllib.request.urlopen('https://www.kaggle.com/api/v1/datasets/view/'+ref,timeout=60))
    (folder/'ficha_kaggle.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding='utf-8')
    archive=folder/(slug+'.zip')
    url=f'https://www.kaggle.com/api/v1/datasets/download/{ref}?datasetVersionNumber={version}'
    if not archive.exists():
        with urllib.request.urlopen(url,timeout=120) as response, archive.with_suffix('.part').open('wb') as f:
            size=0
            while block:=response.read(1024*1024):
                f.write(block); size+=len(block)
                if size%(20*1024**2)<1024**2: print(f'{slug}: {size//1024**2} MB',flush=True)
        archive.with_suffix('.part').replace(archive)
    root=(folder/'original').resolve()
    with zipfile.ZipFile(archive) as z:
        for name in z.namelist():
            if not (root/name).resolve().is_relative_to(root): raise ValueError('Ruta ZIP insegura')
        z.extractall(root)
    with archive.open('rb') as f: digest=hashlib.file_digest(f,'sha256').hexdigest()
    (folder/'procedencia.json').write_text(json.dumps({'ref':ref,'version':version,'url':url,'sha256':digest,
      'author':meta.get('ownerName'),'license_declared':meta.get('licenseName')},indent=2),encoding='utf-8')
    print('Extraído:',root,flush=True)

if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('--ref',default='obulisainaren/forest-fire-c4'); p.add_argument('--version',type=int,default=2); p.add_argument('--slug',default='forest_fire_c4')
    a=p.parse_args(); fetch(a.ref,a.version,a.slug)
