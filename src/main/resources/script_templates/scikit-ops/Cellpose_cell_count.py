# Count cells with Cellpose 3 or 4 (scikit-ops), and outline them.
#@ ImagePlus imp
#@ String (label = "Model", choices = {"cpsam_v2", "cpsam", "cpdino", "cpdino-vitb", "cyto3"}) model
#@ Double (label = "Cell diameter (0 = automatic)", value = 0) diameter
#@ Double (label = "Flow threshold", value = 0.4, min = 0, max = 3, stepSize = 0.05) flowThreshold
#@ Double (label = "Cell probability threshold", value = 0, min = -6, max = 6, stepSize = 0.1) cellprobThreshold
#@ Integer (label = "Smallest object (pixels)", value = 20) minSize

from ij import IJ
from ij.plugin.filter import ThresholdToSelection
from ij.plugin.frame import RoiManager
from ij.process import ImageProcessor
from java.awt import Color
from java.lang import System
from net.imglib2.img.display.imagej import ImageJFunctions
from net.imglib2.roi.labeling import LabelRegions
from fiji_skopkit import skop

start = System.currentTimeMillis()

if model == "cyto3":
  labels = skop.segment.cellpose3(imp, model="cyto3", diameter=diameter,
                                   flow_threshold=flowThreshold,
                                   cellprob_threshold=cellprobThreshold)
else:
  labels = skop.segment.cellpose4(imp, model=model, diameter=diameter,
                                   flow_threshold=flowThreshold,
                                   cellprob_threshold=cellprobThreshold)

regions = LabelRegions(labels)
mask = ImageJFunctions.wrapFloat(labels.getIndexImg(), "Cellpose labels").duplicate()
ip = mask.getProcessor()

# One ROI per cell in the ROI Manager, cyan so it shows on a yellow image.
# Each label value is selected on its own, so touching cells stay apart.
rm = RoiManager.getRoiManager()
rm.reset()
count = 0
# fiji-skopkit's labels are strings, "1", "2", ...; the mask holds the numbers.
for label in sorted(regions.getExistingLabels(), key=int):
  if regions.getLabelRegion(label).size() < minSize:
    continue
  value = int(label)
  ip.setThreshold(value, value, ImageProcessor.NO_LUT_UPDATE)
  roi = ThresholdToSelection.run(mask)
  roi.setStrokeColor(Color.CYAN)
  roi.setName("cell %d" % value)
  rm.addRoi(roi)
  count += 1

IJ.run(imp, "Enhance Contrast", "saturated=0.35")
rm.runCommand(imp, "Show All")

IJ.log("Count: %d" % count)
IJ.log("Processing: %d ms" % (System.currentTimeMillis() - start))

