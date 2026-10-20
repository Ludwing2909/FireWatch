"""La misma decisión que FireClassifier.Analysis en Android; no estima severidad."""
from pathlib import Path
import json
import numpy as np
from PIL import Image, ImageOps

def image_tensor(image,size=160):
    return np.asarray(image.convert('RGB').resize((size,size),Image.Resampling.BILINEAR),np.float32)[None]/255.

class Predictor:
    def __init__(self,model,contract):
        import tensorflow as tf
        self.engine=tf.lite.Interpreter(model_path=str(model),num_threads=4)
        self.engine.allocate_tensors()
        self.inp=self.engine.get_input_details()[0]['index']; self.out=self.engine.get_output_details()[0]['index']
        self.contract=json.loads(Path(contract).read_text(encoding='utf-8'))
        self.ft,self.st=self.contract['thresholds']

    def scores(self,image):
        self.engine.set_tensor(self.inp,image_tensor(image)); self.engine.invoke()
        return self.engine.get_tensor(self.out)[0].copy()

    def analyze(self,image):
        image=ImageOps.exif_transpose(image).convert('RGB')
        p=self.scores(image)
        width,height=image.size
        # Java Math.round redondea .5 hacia arriba.
        cw,ch=max(1,int(width*.65+.5)),max(1,int(height*.65+.5))
        crops=[]
        for row in range(2):
            for col in range(2):
                x=0 if col==0 else width-cw; y=0 if row==0 else height-ch
                crops.append(self.scores(image.crop((x,y,x+cw,y+ch))).tolist())
        count=sum(v[0]>=min(.95,self.ft+.10) for v in crops)
        fire=bool(p[0]>=self.ft or count>=2); smoke=bool(p[1]>=self.st)
        localized=bool(fire and 0<count<=2)
        uncertain=bool(not fire and not smoke and (p[0]>=self.ft-self.contract['uncertain_margin'] or p[1]>=self.st-self.contract['uncertain_margin']))
        return {'fire_score':float(p[0]),'smoke_score':float(p[1]),'fire_regions':count,'fire':fire,'smoke':smoke,'localized':localized,'uncertain':uncertain,'crop_scores':crops}
