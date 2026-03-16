<template>
  <div class="cube-loader" aria-hidden="true">
    <svg viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg" class="loader">
      <defs>
        <pattern id="cube-grid-top" width="12" height="12" patternUnits="userSpaceOnUse" patternTransform="rotate(25)">
          <rect x="0" y="0" width="12" height="12" class="grid-line" />
        </pattern>
        <pattern id="cube-grid-left" width="12" height="12" patternUnits="userSpaceOnUse" patternTransform="skewY(15)">
          <rect x="0" y="0" width="12" height="12" class="grid-line" />
        </pattern>
        <pattern id="cube-grid-right" width="12" height="12" patternUnits="userSpaceOnUse" patternTransform="skewY(-15)">
          <rect x="0" y="0" width="12" height="12" class="grid-line" />
        </pattern>
      </defs>

      <ellipse class="shadow" cx="60" cy="89" rx="26" ry="8" />

      <g class="cube-root">
        <polygon class="face face-top" points="60 18 88 32 60 46 32 32" />
        <polygon class="face face-left" points="32 32 60 46 60 78 32 64" />
        <polygon class="face face-right" points="88 32 60 46 60 78 88 64" />

        <polygon class="face-grid" points="60 18 88 32 60 46 32 32" fill="url(#cube-grid-top)" />
        <polygon class="face-grid" points="32 32 60 46 60 78 32 64" fill="url(#cube-grid-left)" />
        <polygon class="face-grid" points="88 32 60 46 60 78 88 64" fill="url(#cube-grid-right)" />

        <polyline class="edge" points="32 32 60 18 88 32" />
        <polyline class="edge" points="32 32 32 64 60 78 88 64 88 32" />
        <line class="edge" x1="60" y1="46" x2="60" y2="78" />
      </g>
    </svg>
  </div>
</template>

<style scoped>
.cube-loader {
  --cube-top: #9ec1ff;
  --cube-left: #6f9cf0;
  --cube-right: #4f7ddf;
  --cube-edge: #2f5fbf;
  --cube-grid: rgba(255, 255, 255, 0.35);
  --cube-shadow: rgba(26, 54, 112, 0.26);
  display: flex;
  align-items: center;
  justify-content: center;
}

:global(html.dark) .cube-loader {
  --cube-top: #7396d8;
  --cube-left: #4f70ac;
  --cube-right: #37588d;
  --cube-edge: #a9c4f7;
  --cube-grid: rgba(226, 236, 255, 0.26);
  --cube-shadow: rgba(0, 0, 0, 0.52);
}

.loader {
  width: min(34vw, 172px);
  height: auto;
  overflow: visible;
  filter: drop-shadow(0 8px 20px rgba(32, 61, 120, 0.2));
}

.cube-root {
  transform-origin: 60px 48px;
  animation: cubeFloat 2.2s ease-in-out infinite;
}

.face {
  stroke: var(--cube-edge);
  stroke-width: 1.4px;
  stroke-linejoin: round;
}

.face-top {
  fill: var(--cube-top);
  animation: topPulse 2.2s ease-in-out infinite;
}

.face-left {
  fill: var(--cube-left);
  animation: sidePulse 2.2s ease-in-out infinite;
}

.face-right {
  fill: var(--cube-right);
  animation: sidePulse 2.2s ease-in-out infinite reverse;
}

.face-grid {
  opacity: 0.55;
}

.grid-line {
  fill: none;
  stroke: var(--cube-grid);
  stroke-width: 1px;
}

.edge {
  stroke: var(--cube-edge);
  stroke-width: 1.35px;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.shadow {
  fill: var(--cube-shadow);
  animation: shadowScale 2.2s ease-in-out infinite;
}

@keyframes cubeFloat {
  0%,
  100% {
    transform: translateY(0) rotate(0deg);
  }

  50% {
    transform: translateY(-5px) rotate(-1.5deg);
  }
}

@keyframes shadowScale {
  0%,
  100% {
    transform: scaleX(1);
    opacity: 0.95;
  }

  50% {
    transform: scaleX(0.88);
    opacity: 0.72;
  }
}

@keyframes topPulse {
  0%,
  100% {
    filter: brightness(1);
  }

  50% {
    filter: brightness(1.08);
  }
}

@keyframes sidePulse {
  0%,
  100% {
    filter: saturate(1);
  }

  50% {
    filter: saturate(1.15);
  }
}

@media (max-width: 768px) {
  .loader {
    width: min(46vw, 150px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .cube-root,
  .face-top,
  .face-left,
  .face-right,
  .shadow {
    animation: none !important;
  }
}
</style>
