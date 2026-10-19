from pathlib import Path
import argparse, json, os
os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL','2')
os.environ.setdefault('MPLCONFIGDIR',str(Path(__file__).resolve().parents[1]/'.tools/matplotlib'))
import numpy as np
from PIL import Image, ImageOps
import tensorflow as tf

ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser(); p.add_argument('image'); args=p.parse_args()
    with Image.open(args.image) as im:
        x=np.asarray(ImageOps.exif_transpose(im).convert('RGB').resize((96,96),Image.Resampling.BILINEAR),dtype=np.float32)[None]/255.
    engine=tf.lite.Interpreter(model_path=str(ROOT/'modelos/bosque_alerta.tflite'))
    engine.allocate_tensors(); engine.set_tensor(engine.get_input_details()[0]['index'],x); engine.invoke()
    score=float(engine.get_tensor(engine.get_output_details()[0]['index'])[0,0])
    print(json.dumps({'clase':'fuego' if score>=.5 else 'sin_fuego','puntaje_fuego':score,'umbral':.5},indent=2))
if __name__=='__main__': main()
