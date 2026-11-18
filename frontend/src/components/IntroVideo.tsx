import React, { useState, useEffect, useRef } from 'react';
import introUrl from '../../public/intro.mp4?url';

interface IntroVideoProps {
  onFinish: () => void;
}

export const IntroVideo: React.FC<IntroVideoProps> = ({ onFinish }) => {
  const [isFadingOut, setIsFadingOut] = useState(false);
  const [shouldRender, setShouldRender] = useState(true);
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    // 1. Check if already played in this browser session
    const hasPlayed = sessionStorage.getItem('matriq_intro_played');
    if (hasPlayed === 'done') {
      setShouldRender(false);
      onFinish();
      return;
    }

    // 2. Check prefers-reduced-motion accessibility preference
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReducedMotion) {
      sessionStorage.setItem('matriq_intro_played', 'done');
      setShouldRender(false);
      onFinish();
      return;
    }

    // 3. Attempt autoplay
    if (videoRef.current) {
      videoRef.current.play().catch(() => {
        handleDismiss(true);
      });
    }
  }, []);

  const handleDismiss = (immediate = false) => {
    sessionStorage.setItem('matriq_intro_played', 'done');
    if (immediate) {
      setShouldRender(false);
      onFinish();
      return;
    }

    setIsFadingOut(true);
    setTimeout(() => {
      setShouldRender(false);
      onFinish();
    }, 700);
  };

  if (!shouldRender) return null;

  return (
    <div
      className={`fixed inset-0 z-50 flex items-center justify-center bg-[#12110f] transition-opacity duration-700 ease-out ${
        isFadingOut ? 'opacity-0 pointer-events-none' : 'opacity-100'
      }`}
      style={{ overflow: 'hidden' }}
    >
      {/* Intro Video Element */}
      <video
        ref={videoRef}
        src={introUrl}
        autoPlay
        muted
        playsInline
        preload="auto"
        controls={false}
        className="w-full h-full object-cover select-none"
        onEnded={() => handleDismiss(false)}
        onError={() => handleDismiss(true)}
      />

      {/* Elegant Skip Intro Button */}
      <div className="absolute top-6 right-6 z-50">
        <button
          aria-label="Skip intro"
          onClick={() => handleDismiss(false)}
          className="group inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-white/10 hover:bg-white/20 text-white/90 hover:text-white border border-white/20 backdrop-blur-sm shadow-md text-xs font-mono tracking-wider transition-all duration-200 cursor-pointer"
        >
          <span>Skip intro</span>
          <span className="text-sm transition-transform duration-200 group-hover:translate-x-0.5">→</span>
        </button>
      </div>

      {/* Industrial Brand Watermark */}
      <div className="absolute bottom-6 left-6 z-40 pointer-events-none">
        <div className="flex items-center space-x-2.5 text-[11px] font-mono tracking-wider text-white/70">
          <span className="font-bold tracking-widest text-white">MATRIQ</span>
          <span className="text-white/40">|</span>
          <span>Material Intelligence & Harmonization Platform</span>
        </div>
      </div>
    </div>
  );
};
