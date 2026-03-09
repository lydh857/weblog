<template>
  <div class="fullscreen-layout">
    <slot />
  </div>
</template>

<style scoped lang="scss">
.fullscreen-layout {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}
</style>
