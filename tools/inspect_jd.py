import sys
import zipfile
import re

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

with zipfile.ZipFile('JDController-OficialFixed.apk', 'r') as z:
    data = z.read('assets/bin/Data/Managed/Metadata/global-metadata.dat')

idx = data.find(b'CoachScreenController.cs')
print("Surrounding cs path:")
print(data[idx-100:idx+400].decode('utf-8', errors='ignore'))
