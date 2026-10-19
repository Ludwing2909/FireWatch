"""Descarga verificable del dataset original; no requiere credenciales si es público."""
from pathlib import Path
import hashlib, json, ssl, zipfile, urllib.request

ROOT = Path(__file__).resolve().parents[1]

def main():
    dest = ROOT / 'dataset'
    dest.mkdir(exist_ok=True)
    # Windows almacena la CA corporativa en el sistema, no en certifi.
    context = ssl.create_default_context()
    url = 'https://www.kaggle.com/api/v1/datasets/download/phylake1337/fire-dataset?datasetVersionNumber=1'
    archive = dest / 'fire-dataset-v1.zip'
    if not archive.exists():
        with urllib.request.urlopen(url, context=context, timeout=120) as response, archive.with_suffix('.part').open('wb') as out:
            total = 0
            while block := response.read(1024 * 1024):
                out.write(block)
                total += len(block)
                if total % (20 * 1024 * 1024) < 1024 * 1024:
                    print(f'{total / 1024**2:.0f} MB descargados', flush=True)
        archive.with_suffix('.part').replace(archive)
    with zipfile.ZipFile(archive) as z:
        root = (dest / 'original').resolve()
        for name in z.namelist():
            if not (root / name).resolve().is_relative_to(root):
                raise ValueError('Ruta insegura en ZIP')
        z.extractall(root)
    meta = {'source': url, 'version': 1, 'sha256': hashlib.file_digest(archive.open('rb'), 'sha256').hexdigest(),
            'license': 'CC0 según ficha Kaggle; conservar atribución', 'author': 'Ahmed Saied (phylake1337)'}
    (dest / 'procedencia.json').write_text(json.dumps(meta, indent=2), encoding='utf-8')
    print('Dataset extraído en', root)

if __name__ == '__main__':
    main()
