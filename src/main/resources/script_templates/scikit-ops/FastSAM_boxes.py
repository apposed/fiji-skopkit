# Draw FastSAM's object boxes (scikit-ops) as an overlay.
#@ ImagePlus imp
#@ Double (label = "Confidence threshold", value = 0.4, min = 0, max = 1, stepSize = 0.05) conf
#@ Double (label = "Overlap threshold (IoU)", value = 0.9, min = 0, max = 1, stepSize = 0.05) iou
#@ Integer (label = "Image size for the model", value = 1024) imgsz
#@ Integer (label = "Most objects", value = 300) maxDet

from ij import IJ
from ij.gui import Overlay, Roi
from java.awt import Color
from java.lang import System
from org.apposed.skop.fiji import Rois
from fiji_skopkit import skop

start = System.currentTimeMillis()

boxes = skop.detect.fastsam(imp, conf=conf, iou=iou, imgsz=imgsz, max_det=maxDet)

overlay = Overlay()
for box in Rois.flatten(boxes):
  x, y = box.realMin(0), box.realMin(1)
  roi = Roi(x, y, box.realMax(0) - x, box.realMax(1) - y)
  roi.setStrokeColor(Color.YELLOW)
  overlay.add(roi)
imp.setOverlay(overlay)

IJ.log("Boxes: %d" % overlay.size())
IJ.log("Processing: %d ms" % (System.currentTimeMillis() - start))

