<script setup lang="ts">
import BrandLogo from '@/components/BrandLogo.vue'

/** 前台页脚：品牌说明 + 快速入口 + 安全提示（热线 12356）。 */
</script>

<template>
  <footer class="site__footer cv-auto">
    <span class="site__footer-orbit" aria-hidden="true" />

    <div class="container site__footer-grid">
      <div class="site__footer-brand">
        <BrandLogo />
        <p>把心事说给懂你的 AI 听。我们不评判，只陪伴，也始终建议你在需要时走向真实的人。</p>
        <span class="site__footer-motto">stay soft · stay here</span>
      </div>

      <nav class="site__footer-links" aria-label="页脚导航">
        <h3>快速入口</h3>
        <RouterLink to="/front/chat">AI 咨询</RouterLink>
        <RouterLink to="/front/mood">情绪日记</RouterLink>
        <RouterLink to="/front/articles">知识库</RouterLink>
      </nav>

      <div class="site__footer-safe">
        <h3>安全提示</h3>
        <p>
          心理健康 AI 助手是 AI 陪伴工具，不能替代专业诊断与治疗。若你或身边的人有自伤、伤人风险，请立即拨打
          <strong>120 / 110</strong>，或心理援助热线 <strong>12356</strong>。
        </p>
      </div>
    </div>

    <div class="container site__footer-bottom">
      <span>© 2026 心理健康 AI 助手 · 演示项目</span>
      <span>隐私加密 · 数据仅用于本地演示</span>
    </div>
  </footer>
</template>

<style scoped lang="scss">
.site__footer {
  position: relative;
  z-index: 1;
  padding: 76px 0 28px;
  overflow: hidden;
  background:
    radial-gradient(circle at 88% 0%, rgba(186, 166, 170, 0.22), transparent 46%),
    radial-gradient(circle at 8% 100%, rgba(209, 138, 92, 0.13), transparent 44%),
    var(--warm-tint-78);
  color: var(--warm-text-regular);

  &::before {
    content: '';
    position: absolute;
    inset: 0 0 auto;
    height: 1px;
    background: linear-gradient(90deg, transparent, rgba(136, 104, 110, 0.35), transparent);
  }
}

.site__footer-orbit {
  position: absolute;
  right: -160px;
  bottom: -240px;
  width: 520px;
  height: 520px;
  border: 1px solid rgba(136, 104, 110, 0.1);
  border-radius: 50%;
  box-shadow:
    0 0 0 46px rgba(136, 104, 110, 0.03),
    0 0 0 104px rgba(136, 104, 110, 0.02),
    0 0 0 178px rgba(136, 104, 110, 0.012);
  pointer-events: none;
}

.site__footer-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1.6fr 0.8fr 1.2fr;
  gap: $space-7;
}

.site__footer-brand p {
  max-width: 340px;
  margin: 18px 0 0;
  color: var(--warm-text-muted);
  font-size: 13px;
  line-height: 1.9;
}

.site__footer-motto {
  display: inline-block;
  margin-top: 18px;
  color: var(--warm-text-muted);
  font-family: $font-mono;
  font-size: 10.5px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.site__footer-grid h3 {
  margin: 0 0 14px;
  color: var(--warm-text-muted);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.site__footer-links {
  display: grid;
  align-content: start;
  gap: 10px;

  a {
    width: fit-content;
    color: var(--warm-text-regular);
    font-size: 13px;
    transition:
      color 0.2s $ease-out,
      transform 0.2s $ease-out;

    &:hover {
      color: var(--warm-brand-strong);
      transform: translateX(4px);
    }

    &:focus-visible {
      @include focus-ring($warm-brand);
    }
  }
}

.site__footer-safe {
  padding: 20px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-card;
  background: var(--warm-paper-66);
  box-shadow: inset 0 1px 0 var(--warm-paper-80);
}

.site__footer-safe p {
  margin: 0;
  color: var(--warm-text-muted);
  font-size: 12px;
  line-height: 1.85;

  strong {
    color: var(--warm-text);
    font-weight: 600;
  }
}

.site__footer-bottom {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: $space-2;
  margin-top: $space-7;
  padding-top: $space-4;
  border-top: 1px solid var(--warm-line);
  color: var(--warm-text-muted);
  font-size: 11.5px;
  letter-spacing: 0.02em;
}

@include media-down($bp-lg) {
  .site__footer-grid {
    grid-template-columns: 1fr 1fr;
  }

  .site__footer-safe {
    grid-column: 1 / -1;
  }
}

@include media-down($bp-md) {
  .site__footer {
    padding-top: 56px;
  }

  .site__footer-grid {
    grid-template-columns: 1fr;
    gap: $space-5;
  }
}
</style>
