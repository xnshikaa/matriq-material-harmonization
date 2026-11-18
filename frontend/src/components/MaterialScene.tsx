import { useEffect, useRef, useState } from 'react';

const vertexSource = `
attribute vec2 position;
void main() { gl_Position = vec4(position, 0.0, 1.0); }
`;

const fragmentSource = `
precision highp float;
uniform vec2 resolution;
uniform vec2 pointer;
uniform float time;
uniform float mode;
uniform float attention;

mat2 rotate(float a) {
  float c = cos(a), s = sin(a);
  return mat2(c, -s, s, c);
}
float box(vec3 p, vec3 size) {
  vec3 q = abs(p) - size;
  return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - .055;
}
vec2 scene(vec3 p) {
  p.yz = rotate(-.27 + pointer.y * .23 + attention * .12) * p.yz;
  p.xz = rotate(.64 + pointer.x * .31 + attention * .21 + sin(time * .38) * .11 + mode * .21) * p.xz;
  p.xy = rotate(-.14 + sin(time * .34) * .06) * p.xy;
  vec2 result = vec2(100.0, 0.0);
  float spread = .39 + .17 * sin(time * .76) + mode * .15 + attention * .11;
  for (int i = 0; i < 7; i++) {
    float index = float(i);
    float layer = index - 3.0;
    vec3 q = p - vec3(sin(index * 2.3) * spread * .66, layer * (.31 + spread * .18),
                        cos(index * 2.0) * spread * .53);
    q.xz = rotate(layer * spread * .17) * q.xz;
    vec2 shape = vec2(box(q, vec3(1.03 - abs(layer) * .10, .092, .79 - abs(layer) * .08)), index);
    if (shape.x < result.x) result = shape;
  }
  return result;
}
vec3 normal(vec3 p) {
  vec2 e = vec2(.002, 0.0);
  return normalize(vec3(scene(p+e.xyy).x-scene(p-e.xyy).x,
                        scene(p+e.yxy).x-scene(p-e.yxy).x,
                        scene(p+e.yyx).x-scene(p-e.yyx).x));
}
float random(vec2 p) { return fract(sin(dot(p, vec2(127.1,311.7)))*43758.5453); }
void main() {
  vec2 uv = (gl_FragCoord.xy - .5 * resolution) / resolution.y;
  vec3 ro = vec3(0.0, .12, 5.75);
  vec3 rd = normalize(vec3(uv * 3.56, -3.4));
  float wash = exp(-dot(uv * vec2(1.13, 1.0), uv * vec2(1.13, 1.0)) * 9.0);
  vec3 color = vec3(.063,.055,.042) + wash * vec3(.11,.076,.041);
  vec2 grid = (uv + pointer * .008) * 31.0;
  vec2 cell = floor(grid);
  float star = random(cell);
  vec2 drift = vec2(sin(time * .46 + star * 25.0), cos(time * .31 + star * 40.0)) * .12;
  float light = 1.0 - smoothstep(0.0, .075, length(fract(grid)-.5-drift));
  color += vec3(1.0,.79,.49) * light * step(.84,star) * (.18 + .24 * wash);

  float distance = 0.0;
  vec2 surface = vec2(0.0);
  bool hit = false;
  for (int step = 0; step < 64; step++) {
    surface = scene(ro + rd * distance);
    if (surface.x < .002) { hit = true; break; }
    distance += surface.x * .84;
    if (distance > 9.0) break;
  }
  if (hit) {
    vec3 p = ro + rd * distance;
    vec3 n = normal(p);
    vec3 r = reflect(rd, n);
    float diffuse = max(0.0, dot(n, normalize(vec3(-.7, .85, .68))));
    float strip = pow(max(0.0, dot(r, normalize(vec3(-.55, .9, .35)))), 12.0);
    float warmStrip = pow(max(0.0, dot(r, normalize(vec3(.8, .14, .7)))), 7.0);
    float reflectedStrip = pow(max(0.0, dot(r, normalize(vec3(.45, -.72, .26)))), 9.0);
    float rim = pow(1.0 - max(0.0, dot(n, -rd)), 3.0);
    float finish = .92 + .08 * sin(p.x * 177.0 + p.z * 94.0);
    float polished = mod(surface.y, 3.0) < .5 ? 1.0 : .28;
    color = (vec3(.34,.214,.066) + diffuse * vec3(.46,.36,.16)) * finish;
    color += strip * vec3(1.0,.88,.55) + warmStrip * vec3(.89,.61,.23) * polished;
    color += max(attention, 0.0) * warmStrip * vec3(.14,.10,.05);
    color += reflectedStrip * vec3(.43,.30,.12) + rim * vec3(.46,.34,.15);
  }
  gl_FragColor = vec4(color, 1.0);
}
`;

function programFor(gl: WebGLRenderingContext) {
  const program = gl.createProgram();
  if (!program) return null;
  for (const [type, source] of [[gl.VERTEX_SHADER, vertexSource], [gl.FRAGMENT_SHADER, fragmentSource]] as const) {
    const shader = gl.createShader(type);
    if (!shader) {
      gl.deleteProgram(program);
      return null;
    }
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    const compiled = gl.getShaderParameter(shader, gl.COMPILE_STATUS);
    if (compiled) gl.attachShader(program, shader);
    gl.deleteShader(shader);
    if (!compiled) {
      gl.deleteProgram(program);
      return null;
    }
  }
  gl.linkProgram(program);
  if (gl.getProgramParameter(program, gl.LINK_STATUS)) return program;
  gl.deleteProgram(program);
  return null;
}

export function MaterialScene({ signup, attention }: { signup: boolean; attention: number }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const sceneRef = useRef<HTMLDivElement>(null);
  const target = useRef({ x: 0, y: 0, mode: 0, attention: 0 });
  const [live, setLive] = useState(false);

  useEffect(() => { target.current.mode = signup ? 1 : 0; target.current.attention = attention; }, [signup, attention]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const scene = sceneRef.current;
    if (!canvas || !scene) return;
    const motion = matchMedia('(prefers-reduced-motion: reduce)');
    const gl = canvas.getContext('webgl', { alpha: false, antialias: false, powerPreference: 'low-power' });
    if (!gl) return;
    const program = programFor(gl);
    if (!program) return;
    const buffer = gl.createBuffer();
    if (!buffer) { gl.deleteProgram(program); return; }
    gl.useProgram(program);
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    const attribute = gl.getAttribLocation(program, 'position');
    gl.enableVertexAttribArray(attribute);
    gl.vertexAttribPointer(attribute, 2, gl.FLOAT, false, 0, 0);

    const resolution = gl.getUniformLocation(program, 'resolution');
    const pointer = gl.getUniformLocation(program, 'pointer');
    const time = gl.getUniformLocation(program, 'time');
    const mode = gl.getUniformLocation(program, 'mode');
    const attentionUniform = gl.getUniformLocation(program, 'attention');
    const smoothing = { x: 0, y: 0, mode: 0, attention: 0 };
    let frame = 0;
    let lastFrame = 0;
    let lost = false;
    let elapsed = 0;

    function draw() {
      if (lost || !gl || !canvas) return;
      gl.viewport(0, 0, canvas.width, canvas.height);
      gl.uniform2f(resolution, canvas.width, canvas.height);
      gl.uniform2f(pointer, smoothing.x, smoothing.y);
      gl.uniform1f(time, elapsed);
      gl.uniform1f(mode, smoothing.mode);
      gl.uniform1f(attentionUniform, smoothing.attention);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    }

    function resize() {
      if (!scene || !canvas) return;
      const bounds = scene.getBoundingClientRect();
      const scale = Math.min(1, 600 / Math.max(bounds.width, bounds.height, 1));
      canvas.width = Math.max(1, Math.round(bounds.width * scale));
      canvas.height = Math.max(1, Math.round(bounds.height * scale));
      draw();
    }

    function animate(now: number) {
      if (motion.matches || lost || document.hidden) return;
      if (!lastFrame || now - lastFrame >= 40) {
        elapsed += lastFrame ? Math.min(66, now - lastFrame) / 1000 : 0;
        lastFrame = now;
        smoothing.x += (target.current.x - smoothing.x) * .13;
        smoothing.y += (target.current.y - smoothing.y) * .13;
        smoothing.mode += (target.current.mode - smoothing.mode) * .10;
        smoothing.attention += (target.current.attention - smoothing.attention) * .13;
        draw();
      }
      frame = requestAnimationFrame(animate);
    }

    function schedule() {
      cancelAnimationFrame(frame);
      lastFrame = 0;
      if (motion.matches) {
        Object.assign(smoothing, { x: 0, y: 0, mode: 0, attention: 0 });
        elapsed = 0;
        draw();
      } else if (!document.hidden && !lost) {
        frame = requestAnimationFrame(animate);
      }
    }

    function fallback(event: Event) {
      event.preventDefault();
      lost = true;
      cancelAnimationFrame(frame);
      setLive(false);
    }

    const stage = scene.closest<HTMLElement>('.auth-stage') ?? scene;
    function point(event: PointerEvent) {
      if (!stage) return;
      const bounds = stage.getBoundingClientRect();
      target.current.x = (event.clientX - bounds.left) / bounds.width * 2 - 1;
      target.current.y = (event.clientY - bounds.top) / bounds.height * 2 - 1;
    }
    function resetPointer() {
      target.current.x = 0;
      target.current.y = 0;
    }
    stage.addEventListener('pointermove', point, { passive: true });
    stage.addEventListener('pointerleave', resetPointer);
    const observer = new ResizeObserver(resize);
    observer.observe(scene);
    canvas.addEventListener('webglcontextlost', fallback);
    motion.addEventListener('change', schedule);
    document.addEventListener('visibilitychange', schedule);
    resize();
    setLive(true);
    schedule();
    return () => {
      observer.disconnect();
      stage.removeEventListener('pointermove', point);
      stage.removeEventListener('pointerleave', resetPointer);
      canvas.removeEventListener('webglcontextlost', fallback);
      motion.removeEventListener('change', schedule);
      document.removeEventListener('visibilitychange', schedule);
      cancelAnimationFrame(frame);
      gl.deleteBuffer(buffer);
      gl.deleteProgram(program);
    };
  }, []);

  return <div ref={sceneRef} className="material-scene" aria-hidden="true">
    <div className="material-fallback" hidden={live}>
      <div className="material-fallback-stack">{Array.from({ length: 7 }, (_, index) =>
        <i key={index} style={{ '--layer': index - 3 } as React.CSSProperties} />)}</div>
    </div>
    <canvas ref={canvasRef} className={live ? 'is-live' : ''} />
  </div>;
}
