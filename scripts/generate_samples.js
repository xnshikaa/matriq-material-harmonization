const fs = require('fs');
const path = require('path');

const sampleDir = path.resolve(__dirname, '../data/sample');
const generatedDir = path.resolve(__dirname, '../data/generated');

if (!fs.existsSync(sampleDir)) fs.mkdirSync(sampleDir, { recursive: true });
if (!fs.existsSync(generatedDir)) fs.mkdirSync(generatedDir, { recursive: true });

// ── Sample 1: CPCL (Chennai Petroleum Corporation Limited) ─────────────────
const cpclSample = `originalMaterialCode,originalDescription,materialCategory,materialType,dimensions,unit,grade,technicalSpecification,manufacturer
PIPE-001,Pipe seamless 6 inch ASTM A106 Gr.B,Piping,Seamless Pipe,6 inch,INCH,Grade B,ASTM A106,MSL India
VALVE-102,Gate Valve 2 inch 150# Flanged WCB,Valves,Gate Valve,2 inch,INCH,WCB,API 600,L&T Valves
FLANGE-05,Flange WNRF 4 inch 300# A105,Piping,WNRF Flange,4 inch,INCH,A105,ASME B16.5,Unimech
TUBE-201,Heat Exchanger Tube 19.05mm OD SS316L,Tubing,Heat Exchanger Tube,19.05 mm,MM,SS316L,ASTM A213,Sandvik
PUMP-301,Centrifugal Water Pump 50 m3/hr,Machinery,Centrifugal Pump,50 m3/hr,M3/HR,CI,API 610,Kirloskar
GASKET-11,Spiral Wound Gasket 6 inch 150# SS316 with Graphite,Sealing,Spiral Wound Gasket,6 inch,INCH,SS316,ASME B16.20,Champion
BOLT-501,Stud Bolt with 2 Nuts 5/8 inch x 3.5 inch B7/2H,Fasteners,Stud Bolt,5/8 x 3.5 in,INCH,Grade B7,ASTM A193,TVS
FITTING-44,90 Deg Elbow LR 6 inch Sch 40 CS ASTM A234 WPB,Piping,Elbow 90 LR,6 inch,INCH,WPB,ASTM A234,Tubacex
INSTR-801,Pressure Gauge 0-10 Bar 1/2 inch NPT SS316,Instrumentation,Pressure Gauge,1/2 in NPT,INCH,SS316,EN 837-1,Wika
LINE-999,Subsea Flowline Pipe 12 inch API 5L PSL2 X65,Piping,Line Pipe,12 inch,INCH,X65,API 5L,Welspun
`;

// ── Sample 2: IOCL (Indian Oil Corporation Limited) ─────────────────────────
const ioclSample = `originalMaterialCode,originalDescription,materialCategory,materialType,dimensions,unit,grade,technicalSpecification,manufacturer
P-9281,SEAMLESS PIPE 150mm CS A106-B,Piping,Seamless Pipe,150 mm,MM,Grade B,ASTM A106,Maharashtra Seamless
V-4412,2" 150LB FLANGED GATE VALVE CS BODY,Valves,Gate Valve,2 in,INCH,WCB,API 600,Audco
FLG-881,4 INCH 300 LBS WELDING NECK FLANGE CARBON STEEL,Piping,WNRF Flange,4 in,INCH,A105,ASME B16.5,Flash Forge
TB-1109,HE TUBE OD 3/4 INCH STAINLESS STEEL 316L,Tubing,Heat Exchanger Tube,0.75 in,INCH,SS316L,ASTM A213,Ratnamani
GSK-302,GASKET SPWD 150# 150mm SS316/GRAPHITE,Sealing,Spiral Wound Gasket,150 mm,MM,SS316,ASME B16.20,Flexitallic
BLT-702,STUD BOLT B7 WITH HEAVY HEX NUTS 2H 5/8" X 3-1/2",Fasteners,Stud Bolt,5/8 in x 3.5 in,INCH,Grade B7,ASTM A193,Unbrako
ELB-991,ELBOW 90 DEG LONG RADIUS 150MM SCH40 A234 WPB,Piping,Elbow 90 LR,150 mm,MM,WPB,ASTM A234,Remi Metals
CAB-601,Armoured Power Cable 4C x 16 sq.mm XLPE/PVC,Electrical,Power Cable,16 sq.mm,SQMM,Copper,IS 7098,Polycab
MOT-550,Induction Motor 15 kW 415V 1440 RPM TEFC,Electrical,Motor,15 kW,KW,Cast Iron,IS 325,Siemens
VALV-990,Ball Valve 1 inch 800# Forged CS Screwed NPT,Valves,Ball Valve,1 in,INCH,A105,API 602,L&T
`;

// ── Sample 3: ONGC (Oil and Natural Gas Corporation) ────────────────────────
const ongcSample = `originalMaterialCode,originalDescription,materialCategory,materialType,dimensions,unit,grade,technicalSpecification,manufacturer
MAT-1003,Carbon steel seamless pipe 6 in A106 Grade B,Piping,Seamless Pipe,6 in,INCH,Grade B,ASTM A106,Jindal SAW
MAT-2045,Gate valve API 600 class 150 2 inch flanged WCB,Valves,Gate Valve,2 inch,INCH,WCB,API 600,BHEL
MAT-3012,Flange weld neck RF 4" class 300 ASTM A105,Piping,WNRF Flange,4 inch,INCH,A105,ASME B16.5,Unimech
MAT-4050,Stainless steel 316L seamless tube 19.05 mm OD,Tubing,Heat Exchanger Tube,19.05 mm,MM,SS316L,ASTM A213,Sandvik
MAT-5080,Spiral wound gasket 6" class 150 SS316 with graphite filler,Sealing,Spiral Wound Gasket,6 inch,INCH,SS316,ASME B16.20,Champion
MAT-6019,B7 stud bolt 5/8 in x 3.5 in with two 2H nuts,Fasteners,Stud Bolt,5/8 in x 3.5 in,INCH,Grade B7,ASTM A193,Sundram Fasteners
MAT-7033,90 deg LR elbow 6 inch NB Schedule 40 ASTM A234-WPB,Piping,Elbow 90 LR,6 inch,INCH,WPB,ASTM A234,Tubacex
MAT-8022,Explosion proof lighting fixture 2x18W LED Zone 1,Electrical,Lighting,2x18W,WATT,Aluminium,IS/IEC 60079,Bajaj
MAT-9099,Centrifugal water pump capacity 50 m3/hr head 40m,Machinery,Centrifugal Pump,50 m3/hr,M3/HR,Cast Iron,API 610,Sulzer
MAT-9555,Line pipe 12 inch seamless API 5L PSL2 Grade X65,Piping,Line Pipe,12 inch,INCH,X65,API 5L,Jindal
`;

fs.writeFileSync(path.join(sampleDir, 'cpcl_sample.csv'), cpclSample);
fs.writeFileSync(path.join(sampleDir, 'iocl_sample.csv'), ioclSample);
fs.writeFileSync(path.join(sampleDir, 'ongc_sample.csv'), ongcSample);

// ── Synthetic Large Dataset (105 records across 3 companies) ──────────────
const categories = ['Piping', 'Valves', 'Fasteners', 'Electrical', 'Instrumentation', 'Machinery', 'Sealing'];
const companies = ['CPCL', 'IOCL', 'ONGC'];
const lines = ['companyName,originalMaterialCode,originalDescription,materialCategory,materialType,dimensions,unit,grade,technicalSpecification,manufacturer'];

const baseTemplates = [
  { desc: "Seamless Pipe {dim} ASTM A106 Gr.B", cat: "Piping", type: "Seamless Pipe", spec: "ASTM A106", grade: "Grade B" },
  { desc: "Gate Valve {dim} 150# Flanged WCB API 600", cat: "Valves", type: "Gate Valve", spec: "API 600", grade: "WCB" },
  { desc: "WNRF Flange {dim} 300# ASTM A105", cat: "Piping", type: "WNRF Flange", spec: "ASME B16.5", grade: "A105" },
  { desc: "Heat Exchanger Tube {dim} SS316L ASTM A213", cat: "Tubing", type: "Heat Exchanger Tube", spec: "ASTM A213", grade: "SS316L" },
  { desc: "Spiral Wound Gasket {dim} 150# SS316 Graphite", cat: "Sealing", type: "Spiral Wound Gasket", spec: "ASME B16.20", grade: "SS316" },
  { desc: "Stud Bolt {dim} ASTM A193 B7 with 2H Nuts", cat: "Fasteners", type: "Stud Bolt", spec: "ASTM A193", grade: "Grade B7" },
  { desc: "90 Deg Elbow LR {dim} Sch 40 ASTM A234 WPB", cat: "Piping", type: "Elbow 90 LR", spec: "ASTM A234", grade: "WPB" },
  { desc: "Line Pipe {dim} Seamless API 5L PSL2 X65", cat: "Piping", type: "Line Pipe", spec: "API 5L", grade: "X65" },
  { desc: "Ball Valve {dim} 800# Forged CS Screwed NPT", cat: "Valves", type: "Ball Valve", spec: "API 602", grade: "A105" },
  { desc: "Check Valve Dual Plate {dim} 150# Wafer WCB", cat: "Valves", type: "Check Valve", spec: "API 594", grade: "WCB" },
  { desc: "Pressure Gauge 0-10 Bar {dim} SS316", cat: "Instrumentation", type: "Pressure Gauge", spec: "EN 837-1", grade: "SS316" },
  { desc: "Armoured Power Cable 4C x {dim} XLPE/PVC", cat: "Electrical", type: "Power Cable", spec: "IS 7098", grade: "Copper" }
];

const dims = [
  { inch: "2 inch", mm: "50 mm" },
  { inch: "3 inch", mm: "80 mm" },
  { inch: "4 inch", mm: "100 mm" },
  { inch: "6 inch", mm: "150 mm" },
  { inch: "8 inch", mm: "200 mm" },
  { inch: "10 inch", mm: "250 mm" },
  { inch: "12 inch", mm: "300 mm" }
];

let counter = 1;
// Generate 35 triads = 105 records total
for (let t = 0; t < baseTemplates.length && counter <= 100; t++) {
  const tmpl = baseTemplates[t];
  for (let d = 0; d < dims.length && counter <= 100; d++) {
    const dimPair = dims[d];
    
    // Company 0: CPCL (Inches)
    lines.push([
      "CPCL",
      `CPCL-MAT-${String(counter).padStart(4, '0')}`,
      `"${tmpl.desc.replace('{dim}', dimPair.inch)}"`,
      tmpl.cat,
      tmpl.type,
      `"${dimPair.inch}"`,
      "INCH",
      tmpl.grade,
      tmpl.spec,
      "Bharat Heavy Industries"
    ].join(','));
    counter++;

    // Company 1: IOCL (MM or upper case variation)
    lines.push([
      "IOCL",
      `IOCL-P-${String(counter).padStart(4, '0')}`,
      `"${tmpl.desc.replace('{dim}', dimPair.mm).toUpperCase()}"`,
      tmpl.cat,
      tmpl.type,
      `"${dimPair.mm}"`,
      "MM",
      tmpl.grade,
      tmpl.spec,
      "Maharashtra Industrial Corp"
    ].join(','));
    counter++;

    // Company 2: ONGC (in / words variation)
    lines.push([
      "ONGC",
      `ONGC-MAT-${String(counter).padStart(4, '0')}`,
      `"${tmpl.desc.replace('{dim}', dimPair.inch.replace(' inch', ' in'))}"`,
      tmpl.cat,
      tmpl.type,
      `"${dimPair.inch}"`,
      "INCH",
      tmpl.grade,
      tmpl.spec,
      "Jindal & L&T Consortium"
    ].join(','));
    counter++;
  }
}

fs.writeFileSync(path.join(generatedDir, 'materials_105_records.csv'), lines.join('\n'));
console.log(`Generated ${counter - 1} records in materials_105_records.csv and 3 demo sample files.`);
