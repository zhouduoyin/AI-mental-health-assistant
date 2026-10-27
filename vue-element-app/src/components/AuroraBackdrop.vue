<script setup lang="ts">
/**
 * 星夜古典洛丽塔背景。
 *
 * 日间是「象牙黄昏」：酒红与香槟金的星图、蕾丝边、月牙与缎带；
 * 夜间切到「午夜墨蓝」：象牙星光、香槟月晕、缓慢旋转的星轨。
 *
 * 全部为固定定位的装饰层，不参与布局、不接收指针。
 * 动效只用 transform / opacity，避免回流与重绘；
 * 滚动期间暂停所有装饰动画：背景是 fixed 的，冻结几帧看不出来，
 * 但能把滚动时的合成开销压到最低。
 * prefers-reduced-motion 时停掉所有动画，只留静态星图。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'

withDefaults(defineProps<{ quiet?: boolean }>(), { quiet: false })

const rootEl = ref<HTMLElement | null>(null)
let idleTimer = 0

function onPageScroll(): void {
  const root = rootEl.value
  if (!root) return

  // 只在状态切换时写 class，避免每个 scroll 事件都触发一次属性变更
  if (!root.classList.contains('aurora--scrolling')) root.classList.add('aurora--scrolling')
  window.clearTimeout(idleTimer)
  idleTimer = window.setTimeout(() => root.classList.remove('aurora--scrolling'), 160)
}

onMounted(() => {
  // 用 capture 才能在 document 上接住聊天记录这类内部滚动容器
  document.addEventListener('scroll', onPageScroll, { passive: true, capture: true })
})

onBeforeUnmount(() => {
  document.removeEventListener('scroll', onPageScroll, { capture: true })
  window.clearTimeout(idleTimer)
})
</script>

<template>
  <div ref="rootEl" class="aurora" :class="{ 'aurora--quiet': quiet }" aria-hidden="true">
    <span class="aurora__sky" />
    <span class="aurora__nebula" />
    <span class="aurora__milky" />

    <span class="aurora__stars aurora__stars--far" />
    <span class="aurora__stars aurora__stars--mid" />
    <span class="aurora__stars aurora__stars--near" />

    <span class="aurora__moon-system">
      <span class="aurora__swirl" />
      <span class="aurora__rays" />
      <span class="aurora__orbit aurora__orbit--outer" />
      <span class="aurora__orbit aurora__orbit--inner" />
      <span class="aurora__orbit-star" />
      <span class="aurora__halo" />
      <span class="aurora__ripple aurora__ripple--a" />
      <span class="aurora__ripple aurora__ripple--b" />
      <span class="aurora__moon" />
    </span>

    <span class="aurora__constellation">
      <svg viewBox="0 0 320 260" fill="none" aria-hidden="true">
        <g class="aurora__const-lines">
          <path
            d="M160 228C120 200 42 154 42 100 42 64 84 46 110 70 130 88 145 112 160 138 175 112 190 88 210 70 236 46 278 64 278 100 278 154 200 200 160 228Z"
          />
        </g>
        <g class="aurora__const-stars">
          <circle cx="160" cy="228" r="2.4" />
          <circle cx="120" cy="200" r="1.8" />
          <circle cx="42" cy="154" r="2" />
          <circle cx="42" cy="100" r="1.7" />
          <circle cx="84" cy="52" r="1.5" />
          <circle cx="110" cy="70" r="2.1" />
          <circle cx="145" cy="112" r="1.5" />
          <circle cx="160" cy="138" r="3" />
          <circle cx="175" cy="112" r="1.5" />
          <circle cx="210" cy="70" r="2.1" />
          <circle cx="236" cy="52" r="1.5" />
          <circle cx="278" cy="100" r="1.7" />
          <circle cx="278" cy="154" r="2" />
          <circle cx="200" cy="200" r="1.8" />
        </g>
      </svg>
    </span>

    <span class="aurora__shooting aurora__shooting--a" />
    <span class="aurora__shooting aurora__shooting--b" />
    <span class="aurora__shooting aurora__shooting--c" />
    <span class="aurora__shooting aurora__shooting--d" />

    <span class="aurora__band" />

    <span class="aurora__spark aurora__spark--a" />
    <span class="aurora__spark aurora__spark--b" />
    <span class="aurora__spark aurora__spark--c" />
    <span class="aurora__spark aurora__spark--d" />
    <span class="aurora__spark aurora__spark--e" />
    <span class="aurora__spark aurora__spark--f" />
    <span class="aurora__spark aurora__spark--g" />
    <span class="aurora__spark aurora__spark--h" />

    <span class="aurora__petals">
      <i /><i /><i /><i /><i /><i />
    </span>

    <span class="aurora__blob aurora__blob--rose" />
    <span class="aurora__blob aurora__blob--gold" />

    <span class="aurora__lace aurora__lace--top" />
    <span class="aurora__lace aurora__lace--bottom" />
    <span class="aurora__lace aurora__lace--left" />
    <span class="aurora__lace aurora__lace--right" />

    <span class="aurora__bow aurora__bow--tl" />
    <span class="aurora__bow aurora__bow--br" />

    <span class="aurora__motes">
      <i /><i /><i /><i /><i /><i /><i /><i />
    </span>
    <span class="aurora__grain" />
  </div>
</template>

<style scoped lang="scss">
.aurora {
  // —— 日间：象牙黄昏 ——
  --a-sky-top: #efe4e5;
  --a-sky-mid: #f5ecdf;
  --a-sky-bottom: #fbf5ea;
  --a-sky-glow: rgba(217, 185, 139, 0.24);
  --a-star: rgba(117, 66, 79, 0.68);
  --a-star-soft: rgba(181, 136, 77, 0.48);
  --a-star-bright: rgba(160, 116, 65, 0.9);
  --a-star-glow: rgba(201, 164, 92, 0.42);
  --a-shoot: rgba(160, 116, 65, 0.78);
  --a-shoot-glow: rgba(201, 164, 92, 0.46);
  --a-moon: #fff9ea;
  --a-moon-shade: #ead8b7;
  --a-moon-crater: rgba(202, 176, 134, 0.34);
  --a-halo: rgba(255, 245, 218, 0.5);
  --a-gold: rgba(181, 136, 77, 0.55);
  --a-gold-soft: rgba(181, 136, 77, 0.22);
  --a-rose: rgba(201, 141, 155, 0.18);
  --a-wine: rgba(117, 66, 79, 0.12);
  --a-cool: rgba(125, 160, 190, 0.14);
  --a-milky: rgba(255, 248, 232, 0.7);
  --a-milky-edge: rgba(201, 141, 155, 0.2);
  --a-band: linear-gradient(
    100deg,
    rgba(201, 141, 155, 0) 0%,
    rgba(201, 141, 155, 0.2) 20%,
    rgba(201, 164, 92, 0.2) 42%,
    rgba(201, 141, 155, 0.18) 64%,
    rgba(201, 141, 155, 0) 100%
  );
  --a-band-opacity: 0.55;
  --a-const-line: rgba(117, 66, 79, 0.52);
  --a-const-star: #b5884d;
  --a-lace: rgba(255, 251, 242, 0.9);
  --a-lace-glow: rgba(181, 136, 77, 0.14);
  --a-lace-opacity: 0.62;
  --a-bow: rgba(201, 141, 155, 0.5);
  --a-bow-soft: rgba(255, 245, 232, 0.62);
  --a-bow-glow: rgba(181, 136, 77, 0.2);
  --a-bow-opacity: 0.6;
  --a-mote-core: rgba(255, 249, 234, 0.95);
  --a-mote-edge: rgba(201, 164, 92, 0.5);
  --a-mote-glow: rgba(201, 164, 92, 0.45);
  --a-spark: rgba(181, 136, 77, 0.85);
  --a-spark-glow: rgba(201, 164, 92, 0.5);
  --a-moon-opacity: 0.58;
  --a-orbit-opacity: 0.28;
  --a-swirl-opacity: 0.45;
  --a-rays-opacity: 0.24;
  --a-ripple: rgba(181, 136, 77, 0.42);
  --a-petal-light: #f6dfe4;
  --a-petal-deep: #c98d9b;
  --a-petal-glow: rgba(201, 141, 155, 0.35);
  --a-grain-opacity: 0.03;

  // 蕾丝 / 蝴蝶结共用同一套 SVG 遮罩，换主题时只换颜色
  --a-lace-mask: url("data:image/svg+xml,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%2264%22%20height%3D%2232%22%20viewBox%3D%220%200%2064%2032%22%3E%3Cpath%20d%3D%22M0%202h64%22%20stroke%3D%22black%22%20stroke-width%3D%222%22%2F%3E%3Cpath%20d%3D%22M0%204Q8%2028%2016%204T32%204T48%204T64%204%22%20fill%3D%22none%22%20stroke%3D%22black%22%20stroke-width%3D%221.6%22%2F%3E%3Ccircle%20cx%3D%229%22%20cy%3D%2223%22%20r%3D%221.8%22%2F%3E%3Ccircle%20cx%3D%2225%22%20cy%3D%2226%22%20r%3D%221.8%22%2F%3E%3Ccircle%20cx%3D%2241%22%20cy%3D%2223%22%20r%3D%221.8%22%2F%3E%3Ccircle%20cx%3D%2257%22%20cy%3D%2226%22%20r%3D%221.8%22%2F%3E%3C%2Fsvg%3E");
  --a-bow-mask: url("data:image/svg+xml,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22120%22%20height%3D%22110%22%20viewBox%3D%220%200%20120%20110%22%3E%3Cpath%20d%3D%22M58%2048C42%2026%2018%2018%2012%2034%206%2049%2030%2060%2058%2050Z%22%2F%3E%3Cpath%20d%3D%22M62%2048C78%2026%20102%2018%20108%2034%20114%2049%2090%2060%2062%2050Z%22%2F%3E%3Cpath%20d%3D%22M55%2054C46%2070%2039%2084%2027%2098L39%2096C49%2082%2057%2068%2063%2056Z%22%2F%3E%3Cpath%20d%3D%22M65%2054C74%2070%2081%2084%2093%2098L81%2096C71%2082%2063%2068%2057%2056Z%22%2F%3E%3Cellipse%20cx%3D%2260%22%20cy%3D%2250%22%20rx%3D%2210%22%20ry%3D%228%22%2F%3E%3C%2Fsvg%3E");

  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  background: linear-gradient(
    180deg,
    var(--a-sky-top) 0%,
    var(--a-sky-mid) 52%,
    var(--a-sky-bottom) 100%
  );
}

// 夜间：午夜墨蓝。作用域样式会补上 data-v，优先级高于下方的日间令牌
html[data-theme='night'] .aurora {
  --a-sky-top: #080a12;
  --a-sky-mid: #0d1019;
  --a-sky-bottom: #151622;
  --a-sky-glow: rgba(125, 160, 190, 0.14);
  --a-star: rgba(255, 248, 232, 0.92);
  --a-star-soft: rgba(224, 188, 198, 0.62);
  --a-star-bright: #fff6df;
  --a-star-glow: rgba(232, 201, 143, 0.45);
  --a-shoot: rgba(255, 246, 220, 0.95);
  --a-shoot-glow: rgba(232, 201, 143, 0.6);
  --a-moon: #fff7e2;
  --a-moon-shade: #d9c4a1;
  --a-moon-crater: rgba(180, 160, 130, 0.35);
  --a-halo: rgba(255, 245, 218, 0.34);
  --a-gold: rgba(226, 192, 138, 0.6);
  --a-gold-soft: rgba(226, 192, 138, 0.18);
  --a-rose: rgba(224, 188, 198, 0.16);
  --a-wine: rgba(117, 66, 79, 0.22);
  --a-cool: rgba(125, 160, 190, 0.14);
  --a-milky: rgba(168, 190, 220, 0.16);
  --a-milky-edge: rgba(224, 188, 198, 0.12);
  --a-band: linear-gradient(
    100deg,
    rgba(224, 188, 198, 0) 0%,
    rgba(224, 188, 198, 0.26) 18%,
    rgba(226, 192, 138, 0.28) 40%,
    rgba(125, 160, 190, 0.2) 62%,
    rgba(224, 188, 198, 0.22) 82%,
    rgba(224, 188, 198, 0) 100%
  );
  --a-band-opacity: 0.9;
  --a-const-line: rgba(226, 192, 138, 0.55);
  --a-const-star: #ffeecf;
  --a-lace: rgba(250, 242, 226, 0.28);
  --a-lace-glow: rgba(226, 192, 138, 0.3);
  --a-lace-opacity: 0.5;
  --a-bow: rgba(216, 180, 191, 0.3);
  --a-bow-soft: rgba(250, 242, 226, 0.14);
  --a-bow-glow: rgba(226, 192, 138, 0.25);
  --a-bow-opacity: 0.48;
  --a-mote-core: rgba(255, 249, 234, 0.95);
  --a-mote-edge: rgba(226, 192, 138, 0.55);
  --a-mote-glow: rgba(226, 192, 138, 0.6);
  --a-spark: rgba(255, 238, 199, 0.9);
  --a-spark-glow: rgba(232, 201, 143, 0.55);
  --a-moon-opacity: 0.95;
  --a-orbit-opacity: 0.55;
  --a-swirl-opacity: 0.55;
  --a-rays-opacity: 0.36;
  --a-ripple: rgba(226, 192, 138, 0.58);
  --a-petal-light: #f0d3da;
  --a-petal-deep: #b78490;
  --a-petal-glow: rgba(232, 192, 138, 0.35);
  --a-grain-opacity: 0.04;
}

.aurora__sky {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 72% 44% at 50% 108%, var(--a-sky-glow), transparent 72%),
    radial-gradient(ellipse 48% 36% at 82% 4%, var(--a-rose), transparent 70%),
    radial-gradient(ellipse 40% 34% at 8% 18%, var(--a-cool), transparent 72%);
}

// —— 星云与银河宽带：只做 transform / opacity 的缓慢呼吸 ——
.aurora__nebula {
  position: absolute;
  inset: -12%;
  background:
    radial-gradient(ellipse 52% 40% at 22% 18%, var(--a-rose), transparent 68%),
    radial-gradient(ellipse 48% 38% at 78% 24%, var(--a-gold-soft), transparent 70%),
    radial-gradient(ellipse 44% 34% at 58% 82%, var(--a-wine), transparent 72%),
    radial-gradient(ellipse 34% 26% at 6% 64%, var(--a-cool), transparent 74%);
  // 不用 filter: blur —— 大图层缩放动画会反复栅格化模糊，低端 GPU 直接掉帧，
  // 径向渐变本身的透明过渡已经够柔
  opacity: 0.82;
  animation: nebula-breathe 26s ease-in-out infinite alternate;
}

.aurora__milky {
  position: absolute;
  top: -18%;
  left: -20%;
  width: 140vw;
  height: 42vh;
  border-radius: 50%;
  background: linear-gradient(
    116deg,
    transparent 0%,
    var(--a-milky) 28%,
    var(--a-milky-edge) 46%,
    var(--a-milky) 62%,
    transparent 100%
  );
  // 用遮罩把长条边缘化开，代替原来 34px 的模糊滤镜
  -webkit-mask: radial-gradient(ellipse 62% 64% at 50% 50%, #000 18%, transparent 76%);
  mask: radial-gradient(ellipse 62% 64% at 50% 50%, #000 18%, transparent 76%);
  opacity: 0.5;
  transform: rotate(-14deg);
  animation: milky-drift 32s ease-in-out infinite alternate;
}

// —— 星场：三层不同密度 / 速度的星点 ——
.aurora__stars {
  position: absolute;
  inset: -8%;
  background-repeat: repeat;
  pointer-events: none;
}

.aurora__stars--far {
  background-image:
    radial-gradient(circle at 12% 22%, var(--a-star-soft) 0 0.6px, transparent 1px),
    radial-gradient(circle at 38% 72%, var(--a-star-soft) 0 0.55px, transparent 0.95px),
    radial-gradient(circle at 74% 16%, var(--a-star-soft) 0 0.6px, transparent 1px),
    radial-gradient(circle at 88% 62%, var(--a-star-soft) 0 0.5px, transparent 0.9px),
    radial-gradient(circle at 54% 46%, var(--a-star-soft) 0 0.65px, transparent 1.05px),
    radial-gradient(circle at 26% 88%, var(--a-star-soft) 0 0.5px, transparent 0.9px),
    radial-gradient(circle at 66% 92%, var(--a-star-soft) 0 0.6px, transparent 1px),
    radial-gradient(circle at 94% 32%, var(--a-star-soft) 0 0.5px, transparent 0.9px);
  background-size:
    240px 240px,
    320px 320px,
    280px 280px,
    360px 360px,
    300px 300px,
    420px 420px,
    260px 260px,
    380px 380px;
  background-position:
    0 0,
    60px 40px,
    130px 10px,
    20px 160px,
    200px 90px,
    90px 230px,
    220px 150px,
    40px 300px;
  opacity: 0.7;
  animation:
    star-twinkle 9s ease-in-out -3s infinite alternate,
    star-drift 170s ease-in-out infinite alternate;
}

.aurora__stars--mid {
  background-image:
    radial-gradient(circle at 18% 28%, var(--a-star) 0 0.8px, transparent 1.3px),
    radial-gradient(circle at 46% 64%, var(--a-star) 0 0.75px, transparent 1.25px),
    radial-gradient(circle at 82% 18%, var(--a-star) 0 0.8px, transparent 1.3px),
    radial-gradient(circle at 62% 86%, var(--a-star) 0 0.7px, transparent 1.2px),
    radial-gradient(circle at 8% 72%, var(--a-star) 0 0.75px, transparent 1.25px),
    radial-gradient(circle at 94% 54%, var(--a-star) 0 0.8px, transparent 1.3px),
    radial-gradient(circle at 32% 48%, var(--a-star-soft) 0 0.65px, transparent 1.1px),
    radial-gradient(circle at 72% 42%, var(--a-star-soft) 0 0.7px, transparent 1.2px);
  background-size:
    200px 200px,
    260px 260px,
    230px 230px,
    310px 310px,
    350px 350px,
    290px 290px,
    330px 330px,
    270px 270px;
  background-position:
    20px 30px,
    110px 80px,
    240px 20px,
    60px 210px,
    180px 140px,
    300px 250px,
    0 300px,
    160px 360px;
  opacity: 0.78;
  animation:
    star-twinkle 6.5s ease-in-out -1s infinite alternate,
    star-drift 140s ease-in-out infinite alternate-reverse;
}

.aurora__stars--near {
  background-image:
    radial-gradient(
      circle at 50% 50%,
      var(--a-star-bright) 0 1.1px,
      var(--a-star-glow) 1.3px 2.8px,
      transparent 4.2px
    ),
    radial-gradient(
      circle at 50% 50%,
      var(--a-star-bright) 0 0.9px,
      var(--a-star-glow) 1.1px 2.2px,
      transparent 3.6px
    ),
    radial-gradient(
      circle at 50% 50%,
      var(--a-star-bright) 0 0.8px,
      var(--a-star-glow) 1px 1.9px,
      transparent 3.2px
    ),
    radial-gradient(circle at 50% 50%, var(--a-star-glow) 0 0.7px, transparent 2px),
    radial-gradient(circle at 50% 50%, var(--a-star-glow) 0 0.6px, transparent 1.8px),
    radial-gradient(
      circle at 50% 50%,
      var(--a-star-bright) 0 0.9px,
      var(--a-star-glow) 1.1px 2.4px,
      transparent 3.8px
    );
  background-size:
    520px 520px,
    640px 640px,
    700px 700px,
    460px 460px,
    580px 580px,
    760px 760px;
  background-position:
    80px 60px,
    300px 180px,
    140px 330px,
    420px 30px,
    20px 430px,
    360px 380px;
  opacity: 0.9;
  animation:
    star-twinkle 4.8s ease-in-out -2s infinite alternate,
    star-drift 210s ease-in-out infinite alternate;
}

// —— 月亮、月晕与星轨 ——
.aurora__moon-system {
  position: absolute;
  top: 7vh;
  right: -6vw;
  width: clamp(250px, 26vw, 400px);
  aspect-ratio: 1;
  pointer-events: none;
}

.aurora__orbit {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: repeating-conic-gradient(
    from 0deg,
    var(--a-gold) 0deg 1.1deg,
    transparent 1.1deg 9deg
  );
  -webkit-mask: radial-gradient(
    circle closest-side,
    transparent 0 70%,
    #000 70.5%,
    #000 71.3%,
    transparent 71.8%
  );
  mask: radial-gradient(
    circle closest-side,
    transparent 0 70%,
    #000 70.5%,
    #000 71.3%,
    transparent 71.8%
  );
  opacity: var(--a-orbit-opacity);
  animation: orbit-spin 96s linear infinite;
}

.aurora__orbit--inner {
  inset: 12%;
  -webkit-mask: radial-gradient(
    circle closest-side,
    transparent 0 74%,
    #000 74.5%,
    #000 75.2%,
    transparent 75.8%
  );
  mask: radial-gradient(
    circle closest-side,
    transparent 0 74%,
    #000 74.5%,
    #000 75.2%,
    transparent 75.8%
  );
  animation-duration: 64s;
  animation-direction: reverse;
}

// 星云涡流：不用 filter: blur，靠柔和的 conic 色标 + 径向遮罩边缘渐隐，
// 旋转时不会每帧重新栅格化滤镜
.aurora__swirl {
  position: absolute;
  inset: -18%;
  border-radius: 50%;
  background: conic-gradient(
    from 12deg,
    transparent 0deg,
    var(--a-rose) 34deg,
    transparent 96deg,
    var(--a-gold-soft) 168deg,
    transparent 224deg,
    var(--a-rose) 296deg,
    transparent 360deg
  );
  -webkit-mask: radial-gradient(
    circle closest-side,
    transparent 0 34%,
    #000 54%,
    transparent 88%
  );
  mask: radial-gradient(circle closest-side, transparent 0 34%, #000 54%, transparent 88%);
  opacity: var(--a-swirl-opacity);
  animation: orbit-spin 150s linear infinite;
}

// 月晕放射线：细金线组成的锥形渐变，只转 transform
.aurora__rays {
  position: absolute;
  inset: 1%;
  border-radius: 50%;
  background: repeating-conic-gradient(
    from 0deg,
    var(--a-gold) 0deg 0.5deg,
    transparent 0.5deg 22deg
  );
  -webkit-mask: radial-gradient(
    circle closest-side,
    transparent 0 42%,
    #000 48% 62%,
    transparent 72%
  );
  mask: radial-gradient(circle closest-side, transparent 0 42%, #000 48% 62%, transparent 72%);
  opacity: var(--a-rays-opacity);
  animation: orbit-spin 120s linear infinite reverse;
}

// 星轨上的一颗巡游星：整层慢转，星点自己再闪
.aurora__orbit-star {
  position: absolute;
  inset: 15%;
  border-radius: 50%;
  animation: orbit-spin 96s linear infinite;

  &::after {
    content: '';
    position: absolute;
    top: 0;
    left: 50%;
    width: 6px;
    height: 6px;
    margin: -3px 0 0 -3px;
    border-radius: 50%;
    background: var(--a-star-bright);
    box-shadow: 0 0 10px 3px var(--a-star-glow);
    animation: star-blink 4.2s ease-in-out infinite;
  }
}

.aurora__halo {
  position: absolute;
  inset: 18%;
  border-radius: 50%;
  background: radial-gradient(circle, var(--a-halo) 0%, transparent 58%);
  animation: halo-breathe 12s ease-in-out infinite;
}

// 月晕涟漪：两圈缓慢扩散的光环，错开半个周期
.aurora__ripple {
  position: absolute;
  inset: 26%;
  border-radius: 50%;
  border: 1px solid var(--a-ripple);
  opacity: 0;
  animation: moon-ripple 11s cubic-bezier(0.2, 0.6, 0.3, 1) infinite;
}

.aurora__ripple--b {
  animation-delay: -5.5s;
}

.aurora__moon {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 40%;
  aspect-ratio: 1;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  background:
    radial-gradient(circle at 38% 30%, rgba(255, 255, 255, 0.92), transparent 20%),
    radial-gradient(circle at 34% 66%, var(--a-moon-crater) 0 5%, transparent 7%),
    radial-gradient(circle at 54% 74%, var(--a-moon-crater) 0 3%, transparent 5%),
    radial-gradient(circle at 46% 52%, var(--a-moon-crater) 0 2%, transparent 4%),
    radial-gradient(
      circle at 48% 46%,
      var(--a-moon) 0 52%,
      var(--a-moon-shade) 72%,
      transparent 74%
    );
  -webkit-mask: radial-gradient(circle at 32% 30%, transparent 0 47%, #000 48.5%);
  mask: radial-gradient(circle at 32% 30%, transparent 0 47%, #000 48.5%);
  opacity: var(--a-moon-opacity);
  animation: moon-breathe 15s ease-in-out infinite;
}

// —— 星座：一颗镶着金线的爱心 ——
.aurora__constellation {
  position: absolute;
  top: 13vh;
  left: 45%;
  width: clamp(148px, 17vw, 268px);
  color: var(--a-const-line);
  opacity: 0.7;
  animation: constellation-float 19s ease-in-out infinite;
  pointer-events: none;

  svg {
    display: block;
    width: 100%;
    height: auto;
    overflow: visible;
  }
}

.aurora__const-lines {
  animation: constellation-pulse 7s ease-in-out infinite;

  path {
    stroke: currentColor;
    stroke-width: 0.9;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
}

.aurora__const-stars {
  fill: var(--a-const-star);
  filter: drop-shadow(0 0 5px var(--a-star-glow));

  circle {
    animation: star-blink 5.5s ease-in-out infinite;

    &:nth-child(2n) {
      animation-delay: -1.6s;
    }

    &:nth-child(3n) {
      animation-delay: -3.2s;
    }

    &:nth-child(5n) {
      animation-delay: -4.4s;
    }
  }
}

// —— 流星 ——
.aurora__shooting {
  --shoot-x: -52vw;
  --shoot-y: 34vh;

  position: absolute;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--a-shoot);
  box-shadow: 0 0 10px 2px var(--a-shoot-glow);
  opacity: 0;
  animation: shoot 14s cubic-bezier(0.25, 0.1, 0.25, 1) infinite;

  &::before {
    content: '';
    position: absolute;
    top: 50%;
    left: 1px;
    width: 142px;
    height: 1.5px;
    border-radius: 2px;
    background: linear-gradient(90deg, var(--a-shoot), transparent);
    transform: translateY(-50%) rotate(-27deg);
    transform-origin: 0 50%;
    opacity: 0.72;
  }
}

.aurora__shooting--a {
  top: 7vh;
  left: 82vw;
  animation-duration: 13s;
  animation-delay: 1.4s;
}

.aurora__shooting--b {
  --shoot-x: -40vw;
  --shoot-y: 28vh;

  top: 25vh;
  left: 96vw;
  animation-duration: 17s;
  animation-delay: 7.2s;

  &::before {
    width: 100px;
  }
}

.aurora__shooting--c {
  --shoot-x: -34vw;
  --shoot-y: 24vh;

  top: 38vh;
  left: 66vw;
  animation-duration: 21s;
  animation-delay: 13.5s;

  &::before {
    width: 76px;
  }
}

// 第四条是「潜伏」的慢流星：间隔更长，轨迹更短，反而更耐看
.aurora__shooting--d {
  --shoot-x: -30vw;
  --shoot-y: 22vh;

  top: 54vh;
  left: 38vw;
  animation-duration: 26s;
  animation-delay: 18s;

  &::before {
    width: 64px;
  }
}

// —— 极光缎带 ——
.aurora__band {
  position: absolute;
  top: -16%;
  left: -28%;
  width: 156vw;
  height: 38vh;
  border-radius: 50%;
  background: var(--a-band);
  // 同样用径向遮罩收边，替代原来的 28px 模糊
  -webkit-mask: radial-gradient(ellipse 58% 62% at 50% 50%, #000 20%, transparent 78%);
  mask: radial-gradient(ellipse 58% 62% at 50% 50%, #000 20%, transparent 78%);
  opacity: var(--a-band-opacity);
  transform: rotate(-8deg) skewX(-7deg);
  animation: aurora-band 28s ease-in-out infinite alternate;
}

// —— 四角星闪光 ——
.aurora__spark {
  position: absolute;
  width: 14px;
  height: 14px;
  background: var(--a-spark);
  clip-path: polygon(
    50% 0%,
    58% 42%,
    100% 50%,
    58% 58%,
    50% 100%,
    42% 58%,
    0% 50%,
    42% 42%
  );
  filter: drop-shadow(0 0 6px var(--a-spark-glow));
  opacity: 0;
  animation: sparkle 6.5s ease-in-out infinite;
}

.aurora__spark--a {
  top: 18%;
  left: 16%;
  animation-delay: 0.4s;
}

.aurora__spark--b {
  top: 36%;
  left: 76%;
  width: 10px;
  height: 10px;
  animation-delay: -2.2s;
}

.aurora__spark--c {
  top: 62%;
  left: 28%;
  width: 18px;
  height: 18px;
  animation-delay: -4s;
}

.aurora__spark--d {
  top: 78%;
  left: 66%;
  width: 9px;
  height: 9px;
  animation-delay: -1.2s;
}

.aurora__spark--e {
  top: 12%;
  left: 58%;
  width: 11px;
  height: 11px;
  animation-delay: -5.1s;
}

.aurora__spark--f {
  top: 46%;
  left: 88%;
  width: 8px;
  height: 8px;
  animation-delay: -3.3s;
}

.aurora__spark--g {
  top: 70%;
  left: 12%;
  width: 13px;
  height: 13px;
  animation-delay: -0.9s;
}

.aurora__spark--h {
  top: 88%;
  left: 44%;
  width: 7px;
  height: 7px;
  animation-delay: -4.7s;
}

// —— 飘落的缎带花瓣：只有 transform / opacity，落下 + 旋转 + 侧向漂移 ——
.aurora__petals {
  position: absolute;
  inset: 0;
  pointer-events: none;

  i {
    position: absolute;
    top: -8vh;
    display: block;
    width: 12px;
    height: 12px;
    border-radius: 72% 10% 72% 10%;
    background: linear-gradient(140deg, var(--a-petal-light), var(--a-petal-deep));
    box-shadow: 0 0 10px var(--a-petal-glow);
    opacity: 0;
    animation: petal-fall 20s linear infinite;

    &:nth-child(1) {
      left: 8%;
      --petal-drift: 46px;
      animation-duration: 23s;
      animation-delay: -2s;
    }

    &:nth-child(2) {
      left: 24%;
      width: 10px;
      height: 10px;
      --petal-drift: -38px;
      animation-duration: 19s;
      animation-delay: -9s;
    }

    &:nth-child(3) {
      left: 41%;
      width: 14px;
      height: 14px;
      --petal-drift: 62px;
      animation-duration: 26s;
      animation-delay: -15s;
    }

    &:nth-child(4) {
      left: 58%;
      --petal-drift: -52px;
      animation-duration: 21s;
      animation-delay: -5s;
    }

    &:nth-child(5) {
      left: 74%;
      width: 9px;
      height: 9px;
      --petal-drift: 34px;
      animation-duration: 24s;
      animation-delay: -12s;
    }

    &:nth-child(6) {
      left: 90%;
      width: 13px;
      height: 13px;
      --petal-drift: -60px;
      animation-duration: 28s;
      animation-delay: -18s;
    }
  }
}

// —— 远景光团 ——
.aurora__blob {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  // drift 动画本身会提升合成层，不必再常驻 will-change
}

.aurora__blob--rose {
  top: -22%;
  right: -12%;
  width: 58vw;
  aspect-ratio: 1;
  min-width: 480px;
  background: radial-gradient(circle, var(--a-rose) 0%, transparent 68%);
  animation: aurora-drift-a 34s ease-in-out infinite;
}

.aurora__blob--gold {
  bottom: -28%;
  left: -14%;
  width: 54vw;
  aspect-ratio: 1;
  min-width: 460px;
  background: radial-gradient(circle, var(--a-gold-soft) 0%, transparent 70%);
  animation: aurora-drift-b 39s ease-in-out infinite;
}

// —— 蕾丝边 ——
.aurora__lace {
  position: absolute;
  background: linear-gradient(
    90deg,
    var(--a-lace),
    color-mix(in srgb, var(--a-lace) 48%, var(--a-gold)),
    var(--a-lace)
  );
  -webkit-mask-image: var(--a-lace-mask);
  mask-image: var(--a-lace-mask);
  -webkit-mask-repeat: repeat-x;
  mask-repeat: repeat-x;
  -webkit-mask-size: 64px 32px;
  mask-size: 64px 32px;
  opacity: var(--a-lace-opacity);
  filter: drop-shadow(0 0 6px var(--a-lace-glow));
  pointer-events: none;
}

.aurora__lace--top {
  top: 0;
  right: 0;
  left: 0;
  height: 32px;
}

.aurora__lace--bottom {
  right: 0;
  bottom: 0;
  left: 0;
  height: 32px;
  transform: scaleY(-1);
}

.aurora__lace--left,
.aurora__lace--right {
  top: 0;
  width: 100vh;
  height: 32px;
}

.aurora__lace--left {
  left: 0;
  transform-origin: 0 0;
  transform: translateX(32px) rotate(90deg);
}

.aurora__lace--right {
  right: 0;
  transform-origin: 100% 0;
  transform: translateX(-32px) rotate(-90deg);
}

// —— 蝴蝶结 ——
.aurora__bow {
  position: absolute;
  width: 92px;
  height: 84px;
  background: linear-gradient(
    135deg,
    var(--a-bow-soft),
    var(--a-bow) 48%,
    var(--a-gold)
  );
  -webkit-mask-image: var(--a-bow-mask);
  mask-image: var(--a-bow-mask);
  -webkit-mask-repeat: no-repeat;
  mask-repeat: no-repeat;
  -webkit-mask-position: center;
  mask-position: center;
  -webkit-mask-size: contain;
  mask-size: contain;
  opacity: var(--a-bow-opacity);
  filter: drop-shadow(0 0 12px var(--a-bow-glow));
  pointer-events: none;
}

.aurora__bow--tl {
  top: 88px;
  left: 18px;
  transform-origin: 50% 20%;
  animation: bow-sway-a 9s ease-in-out infinite alternate;
}

.aurora__bow--br {
  right: 22px;
  bottom: 30px;
  width: 120px;
  height: 110px;
  transform-origin: 50% 20%;
  animation: bow-sway-b 11s ease-in-out infinite alternate;
}

// —— 漂浮的珍珠微尘 ——
.aurora__motes {
  position: absolute;
  inset: 0;

  i {
    position: absolute;
    display: block;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: radial-gradient(
      circle,
      var(--a-mote-core),
      var(--a-mote-edge) 55%,
      transparent 72%
    );
    box-shadow: 0 0 12px var(--a-mote-glow);
    opacity: 0;
    animation: mote-drift 18s linear infinite;

    &:nth-child(1) {
      top: 20%;
      left: 10%;
      animation-delay: 0s;
      animation-duration: 17s;
    }

    &:nth-child(2) {
      top: 34%;
      left: 74%;
      width: 8px;
      height: 8px;
      animation-delay: 3.5s;
      animation-duration: 21s;
    }

    &:nth-child(3) {
      top: 62%;
      left: 26%;
      width: 4px;
      height: 4px;
      animation-delay: 6s;
      animation-duration: 15s;
    }

    &:nth-child(4) {
      top: 50%;
      left: 90%;
      width: 5px;
      height: 5px;
      animation-delay: 9s;
      animation-duration: 19s;
    }

    &:nth-child(5) {
      top: 76%;
      left: 56%;
      width: 7px;
      height: 7px;
      animation-delay: 2s;
      animation-duration: 23s;
    }

    &:nth-child(6) {
      top: 13%;
      left: 42%;
      width: 4px;
      height: 4px;
      animation-delay: 12s;
      animation-duration: 16s;
    }

    &:nth-child(7) {
      top: 44%;
      left: 6%;
      width: 5px;
      height: 5px;
      animation-delay: 14s;
      animation-duration: 24s;
    }

    &:nth-child(8) {
      top: 84%;
      left: 84%;
      width: 6px;
      height: 6px;
      animation-delay: 7.5s;
      animation-duration: 20s;
    }
  }
}

// —— 纸面颗粒 ——
.aurora__grain {
  position: absolute;
  inset: 0;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.82' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='160' height='160' filter='url(%23n)' opacity='0.55'/%3E%3C/svg%3E");
  // 不用 mix-blend-mode：全屏混合会让整层背景每帧重新合成
  opacity: var(--a-grain-opacity);
}

// 滚动时冻结装饰动画。背景是 fixed 的，暂停几帧看不出差别，
// 但能把滚动期间的合成 / 重绘开销降到最低。
.aurora--scrolling *,
.aurora--scrolling *::before,
.aurora--scrolling *::after {
  animation-play-state: paused !important;
}

.aurora--quiet {
  .aurora__stars--far {
    opacity: 0.36;
  }

  .aurora__constellation {
    opacity: 0.28;
  }

  .aurora__bow--br {
    opacity: 0.28;
  }

  .aurora__shooting--c,
  .aurora__shooting--d,
  .aurora__spark--c,
  .aurora__spark--e,
  .aurora__spark--f,
  .aurora__spark--h,
  .aurora__rays {
    display: none;
  }

  .aurora__petals i:nth-child(n + 4) {
    display: none;
  }

  .aurora__swirl {
    opacity: 0.26;
  }

  .aurora__band,
  .aurora__milky {
    opacity: 0.4;
  }

  .aurora__orbit--inner {
    display: none;
  }
}

@keyframes star-drift {
  from {
    transform: translate3d(0, 0, 0);
  }

  to {
    transform: translate3d(-26px, 18px, 0);
  }
}

@keyframes star-twinkle {
  from {
    opacity: 0.55;
  }

  to {
    opacity: 1;
  }
}

@keyframes star-blink {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.28;
  }
}

@keyframes nebula-breathe {
  0% {
    opacity: 0.68;
    transform: translate3d(-1%, 0, 0) scale(1);
  }

  100% {
    opacity: 0.94;
    transform: translate3d(2%, -1.5%, 0) scale(1.07);
  }
}

@keyframes milky-drift {
  0% {
    transform: rotate(-15deg) translate3d(-2%, 0, 0) scale(1);
  }

  100% {
    transform: rotate(-9deg) translate3d(3%, 2%, 0) scale(1.06);
  }
}

@keyframes aurora-band {
  0% {
    transform: translate3d(-4%, 0, 0) rotate(-9deg) skewX(-8deg) scaleY(0.92);
  }

  50% {
    transform: translate3d(2%, 3%, 0) rotate(-5deg) skewX(-4deg) scaleY(1.08);
  }

  100% {
    transform: translate3d(6%, -2%, 0) rotate(-11deg) skewX(-10deg) scaleY(0.96);
  }
}

@keyframes aurora-drift-a {
  0%,
  100% {
    transform: translate3d(-4%, -2%, 0) scale(1);
  }

  50% {
    transform: translate3d(6%, 5%, 0) scale(1.14);
  }
}

@keyframes aurora-drift-b {
  0%,
  100% {
    transform: translate3d(3%, 4%, 0) scale(1.08);
  }

  50% {
    transform: translate3d(-6%, -4%, 0) scale(0.94);
  }
}

@keyframes orbit-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes moon-breathe {
  0%,
  100% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 0.9;
  }

  50% {
    transform: translate(-50%, -50%) scale(1.035);
    opacity: 1;
  }
}

@keyframes halo-breathe {
  0%,
  100% {
    transform: scale(0.94);
    opacity: 0.5;
  }

  50% {
    transform: scale(1.06);
    opacity: 0.95;
  }
}

@keyframes moon-ripple {
  0% {
    opacity: 0;
    transform: scale(0.86);
  }

  18% {
    opacity: 0.7;
  }

  70% {
    opacity: 0.16;
  }

  100% {
    opacity: 0;
    transform: scale(1.9);
  }
}

@keyframes petal-fall {
  0% {
    opacity: 0;
    transform: translate3d(0, 0, 0) rotate(0deg) scale(0.9);
  }

  12% {
    opacity: 0.62;
  }

  62% {
    opacity: 0.46;
  }

  100% {
    opacity: 0;
    transform: translate3d(var(--petal-drift, 30px), 116vh, 0) rotate(320deg) scale(1.05);
  }
}

@keyframes constellation-float {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }

  50% {
    transform: translate3d(0, -8px, 0);
  }
}

@keyframes constellation-pulse {
  0%,
  100% {
    opacity: 0.55;
  }

  50% {
    opacity: 1;
  }
}

@keyframes shoot {
  0%,
  68% {
    opacity: 0;
    transform: translate3d(0, 0, 0);
  }

  70% {
    opacity: 0.95;
  }

  84% {
    opacity: 0.72;
    transform: translate3d(var(--shoot-x), var(--shoot-y), 0);
  }

  86%,
  100% {
    opacity: 0;
    transform: translate3d(
      calc(var(--shoot-x) - 4vw),
      calc(var(--shoot-y) + 3vh),
      0
    );
  }
}

@keyframes sparkle {
  0%,
  100% {
    opacity: 0;
    transform: scale(0.2) rotate(0deg);
  }

  42% {
    opacity: 0.9;
    transform: scale(1) rotate(18deg);
  }

  58% {
    opacity: 0.55;
    transform: scale(0.8) rotate(30deg);
  }
}

@keyframes bow-sway-a {
  from {
    transform: rotate(-8deg) translate3d(0, 0, 0);
  }

  to {
    transform: rotate(-3deg) translate3d(0, -6px, 0);
  }
}

@keyframes bow-sway-b {
  from {
    transform: rotate(8deg) translate3d(0, 0, 0);
  }

  to {
    transform: rotate(3deg) translate3d(0, 5px, 0);
  }
}

@keyframes mote-drift {
  0% {
    opacity: 0;
    transform: translate3d(0, 26px, 0) scale(0.8);
  }

  18% {
    opacity: 0.78;
  }

  70% {
    opacity: 0.42;
  }

  100% {
    opacity: 0;
    transform: translate3d(18px, -96px, 0) scale(1.15);
  }
}

@include media-down($bp-lg) {
  .aurora__moon-system {
    right: 5vw;
    width: clamp(220px, 32vw, 330px);
  }

  .aurora__constellation {
    right: 28vw;
    left: auto;
    width: 170px;
  }

  .aurora__lace--left,
  .aurora__lace--right {
    display: none;
  }

  .aurora__bow--br {
    display: none;
  }
}

@include media-down($bp-md) {
  .aurora__moon-system {
    top: 84px;
    right: 2vw;
    width: 190px;
  }

  .aurora__orbit,
  .aurora__orbit-star,
  .aurora__rays,
  .aurora__constellation {
    display: none;
  }

  .aurora__bow {
    display: none;
  }

  .aurora__shooting--b,
  .aurora__shooting--c,
  .aurora__shooting--d {
    display: none;
  }

  .aurora__petals i:nth-child(n + 4) {
    display: none;
  }

  .aurora__stars--near {
    opacity: 0.7;
  }

  .aurora__lace--top,
  .aurora__lace--bottom {
    height: 24px;
    -webkit-mask-size: 48px 24px;
    mask-size: 48px 24px;
  }

  .aurora__motes i:nth-child(n + 6) {
    display: none;
  }
}

@include media-down($bp-sm) {
  .aurora__moon-system {
    top: 78px;
    width: 140px;
    opacity: 0.8;
  }

  .aurora__spark--a,
  .aurora__spark--b,
  .aurora__spark--f,
  .aurora__spark--g,
  .aurora__spark--h {
    display: none;
  }

  .aurora__swirl,
  .aurora__ripple--b {
    display: none;
  }

  .aurora__blob--rose,
  .aurora__blob--gold {
    opacity: 0.7;
  }
}

@media (prefers-reduced-motion: reduce) {
  .aurora__nebula,
  .aurora__milky,
  .aurora__stars,
  .aurora__swirl,
  .aurora__rays,
  .aurora__orbit,
  .aurora__orbit-star,
  .aurora__orbit-star::after,
  .aurora__halo,
  .aurora__ripple,
  .aurora__moon,
  .aurora__constellation,
  .aurora__const-lines,
  .aurora__const-stars circle,
  .aurora__shooting,
  .aurora__band,
  .aurora__spark,
  .aurora__petals i,
  .aurora__blob,
  .aurora__bow,
  .aurora__motes i {
    animation: none !important;
  }

  .aurora__shooting,
  .aurora__spark {
    display: none;
  }

  .aurora__stars--far {
    opacity: 0.55;
  }

  .aurora__motes i {
    opacity: 0.4;
  }
}
</style>
