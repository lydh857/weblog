<template>
  <div class="cube-loader" aria-hidden="true">
    <img class="cube-image" src="/brand/startup-cube.svg" alt="" />
    <p class="cube-loader__text">正在加载页面...</p>
  </div>
</template>

<style scoped>
.cube-loader {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.cube-image {
  width: min(34vw, 172px);
  height: auto;
  filter: drop-shadow(0 8px 20px rgba(32, 61, 120, 0.2));
  animation: cubeFloat 2.2s ease-in-out infinite;
}

.cube-loader__text {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.6px;
  color: #4e6ea8;
}

:global(html.dark) .cube-loader__text {
  color: #aac5f7;
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

@media (max-width: 768px) {
  .cube-image {
    width: min(46vw, 150px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .cube-image {
    animation: none !important;
  }
}
</style>
