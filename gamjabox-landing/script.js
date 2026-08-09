const icons = {
  server: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="4" width="18" height="6" rx="2"/><rect x="3" y="14" width="18" height="6" rx="2"/><path d="M7 7h.01M7 17h.01"/></svg>',
  globe: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c2.2 2.5 3.3 5.5 3.3 9S14.2 18.5 12 21c-2.2-2.5-3.3-5.5-3.3-9S9.8 5.5 12 3Z"/></svg>',
  users: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
  key: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="7.5" cy="15.5" r="4.5"/><path d="m10.7 12.3 8-8M15 8l2 2M18 5l2 2"/></svg>',
  activity: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 12h4l2.2-7 4.3 14 2.2-7H21"/></svg>',
  cpu: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="6" y="6" width="12" height="12" rx="2"/><rect x="9" y="9" width="6" height="6"/><path d="M9 1v3M15 1v3M9 20v3M15 20v3M20 9h3M20 14h3M1 9h3M1 14h3"/></svg>',
  memory: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="7" width="18" height="10" rx="2"/><path d="M7 10v4M11 10v4M15 10v4M19 10v4M7 17v3M11 17v3M15 17v3M19 17v3"/></svg>',
  shield: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/><path d="m9 12 2 2 4-4"/></svg>',
  rocket: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4.5 16.5c-1.5 1.3-2 5-2 5s3.7-.5 5-2M9 15l-3-3a18 18 0 0 1 9-8.5c2.3-1 5.5-1 5.5-1s0 3.2-1 5.5A18 18 0 0 1 11 17l-2-2Z"/><circle cx="15" cy="8" r="2"/></svg>',
  terminal: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="m7 9 3 3-3 3M13 15h4"/></svg>',
  folder: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 6a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v9a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V6Z"/></svg>',
  git: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="6" cy="5" r="2"/><circle cx="6" cy="19" r="2"/><circle cx="18" cy="12" r="2"/><path d="M6 7v10M8 7c0 4 8 1 8 5"/></svg>',
  box: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z"/><path d="m4.3 7.7 7.7 4.2 7.7-4.2M12 12v9"/></svg>',
  sliders: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6"/></svg>',
  zap: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M13 2 3 14h8l-1 8 10-12h-8l1-8Z"/></svg>',
  code: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m8 9-3 3 3 3M16 9l3 3-3 3M14 5l-4 14"/></svg>',
  pulse: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 12h4l2.2-7 4.3 14 2.2-7H21"/></svg>',
  cloud: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M17.5 19H6a4 4 0 1 1 1.3-7.8A6 6 0 0 1 19 9.5 4.7 4.7 0 0 1 17.5 19Z"/></svg>',
  layout: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9"/></svg>',
  lock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>',
  book: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20V4H6.5A2.5 2.5 0 0 0 4 6.5v13Z"/><path d="M4 19.5A2.5 2.5 0 0 0 6.5 22H20v-5"/></svg>',
  message: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v8Z"/><path d="M8 9h8M8 13h5"/></svg>',
  logout: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/></svg>',
  search: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>',
  preview: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18M8 4v5M8 14h3M14 14h2M8 17h8"/></svg>',
  database: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6"/></svg>',
  user: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>',
  settings: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3 1.7 1.7 0 0 0 1-1.6v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/></svg>',
  layers: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m12 2 9 5-9 5-9-5 9-5Z"/><path d="m3 12 9 5 9-5M3 17l9 5 9-5"/></svg>'
};

document.querySelectorAll('[data-icon]').forEach((node) => {
  const name = node.dataset.icon;
  if (icons[name]) node.innerHTML = icons[name];
});

const revealElements = document.querySelectorAll('.reveal');
const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

[
  [...document.querySelectorAll('.feature-grid > .reveal')],
  [...document.querySelectorAll('.capability-grid > .reveal')],
].forEach((group) => {
  group.forEach((element, index) => {
    element.style.setProperty('--reveal-order', String(index % 3));
  });
});

if (prefersReducedMotion || !('IntersectionObserver' in window)) {
  revealElements.forEach((element) => element.classList.add('is-visible'));
} else {
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      entry.target.classList.toggle('is-visible', entry.isIntersecting);
    });
  }, { threshold: 0.12, rootMargin: '0px 0px -4% 0px' });

  revealElements.forEach((element) => observer.observe(element));
}

const siteHeader = document.querySelector('.site-header');
const navigationLinks = [...document.querySelectorAll('.desktop-nav a[href^="#"]')];
const navigationItems = navigationLinks
  .map((link) => ({
    link,
    section: document.querySelector(link.getAttribute('href')),
  }))
  .filter((item) => item.section);
let activeNavigationId;
let navigationFrame;

const updateHeaderState = () => {
  siteHeader?.classList.toggle('is-scrolled', window.scrollY > 16);
};

const updateActiveNavigation = () => {
  const probePosition = window.scrollY + (siteHeader?.offsetHeight ?? 0) + 20;
  let activeItem;

  navigationItems.forEach((item) => {
    if (probePosition >= item.section.offsetTop) activeItem = item;
  });

  const nextActiveId = activeItem?.section.id;
  if (nextActiveId === activeNavigationId) return;
  activeNavigationId = nextActiveId;

  navigationItems.forEach((item) => {
    const isActive = item.section.id === activeNavigationId;
    item.link.classList.toggle('is-active', isActive);
    if (isActive) item.link.setAttribute('aria-current', 'location');
    else item.link.removeAttribute('aria-current');
  });
};

const scheduleNavigationUpdate = () => {
  if (navigationFrame) return;
  navigationFrame = window.requestAnimationFrame(() => {
    navigationFrame = undefined;
    updateHeaderState();
    updateActiveNavigation();
  });
};

updateHeaderState();
updateActiveNavigation();
window.addEventListener('scroll', scheduleNavigationUpdate, { passive: true });

const smoothScrollMedia = window.matchMedia('(min-width: 821px) and (prefers-reduced-motion: no-preference)');
const rootElement = document.documentElement;

// 데스크톱에서는 이 스크립트의 JS 엔진이 모든 스무스 스크롤을 담당하므로 CSS
// `scroll-behavior: smooth`를 꺼 둔다. 켜 두면 씬 전환 후 위치를 즉시 맞추는
// scrollTo가 브라우저 렌더 시점에 CSS smooth로 다시 해석돼, 화면이 뒤늦게 스르륵
// 자리를 찾아가는 현상이 생긴다. 모바일/모션축소 환경에서는 앵커 링크의 네이티브
// 스무스 스크롤을 위해 CSS 기본값을 유지한다.
const syncNativeScrollBehavior = () => {
  rootElement.style.scrollBehavior = smoothScrollMedia.matches ? 'auto' : '';
};
syncNativeScrollBehavior();

const sceneDefinitions = [
  {
    id: 'top',
    anchor: document.querySelector('.hero'),
    elements: [
      document.querySelector('.hero'),
      document.querySelector('.trust-strip'),
    ],
  },
  { id: 'manifesto', anchor: document.querySelector('.manifesto'), elements: [document.querySelector('.manifesto')] },
  { id: 'features', anchor: document.querySelector('#features'), elements: [document.querySelector('#features')] },
  { id: 'operations', anchor: document.querySelector('#operations'), elements: [document.querySelector('#operations')] },
  { id: 'workflow', anchor: document.querySelector('#workflow'), elements: [document.querySelector('#workflow')] },
  {
    id: 'architecture',
    anchor: document.querySelector('#architecture'),
    elements: [document.querySelector('#architecture')],
  },
  {
    id: 'final',
    // 푸터는 씬 크로스페이드(스케일)에 넣지 않는다. final-cta만 페이드시키고
    // 푸터는 자체 reveal 로 아래에서 위로 슥 올라오게 해 마지막 화면을 마무리한다.
    anchor: document.querySelector('.final-cta'),
    elements: [document.querySelector('.final-cta')],
  },
]
  .filter((scene) => scene.anchor)
  .map((scene) => ({ ...scene, elements: scene.elements.filter(Boolean) }));

sceneDefinitions.forEach((scene, index) => {
  scene.elements.forEach((element) => element.classList.add('scene-fade-target'));
  if (index > 0) scene.anchor.classList.add('scene-panel');
});

let renderedScroll = window.scrollY;
let targetScroll = window.scrollY;
let lastWheelAt = 0;
let lastFrameAt = performance.now();
let scrollFrame;
let scrollEngineRunning = false;
let previousScrollBehavior = rootElement.style.scrollBehavior;
let sceneTransitionRunning = false;
let boundaryGestureArmed = false;
let boundaryDirection = 0;
let boundaryPeakDelta = 0;
let boundaryLastDelta = 0;
let boundaryHasSettled = false;

const wait = (milliseconds) => new Promise((resolve) => window.setTimeout(resolve, milliseconds));

const resetBoundaryGesture = () => {
  boundaryGestureArmed = false;
  boundaryDirection = 0;
  boundaryPeakDelta = 0;
  boundaryLastDelta = 0;
  boundaryHasSettled = false;
};

const armBoundaryGesture = (direction, magnitude) => {
  boundaryGestureArmed = true;
  boundaryDirection = direction;
  boundaryPeakDelta = magnitude;
  boundaryLastDelta = magnitude;
  boundaryHasSettled = magnitude <= 8;
};

const clampScroll = (value) => Math.min(
  Math.max(0, value),
  Math.max(0, rootElement.scrollHeight - window.innerHeight),
);

const getSceneStart = (index) => (index === 0 ? 0 : sceneDefinitions[index].anchor.offsetTop);

const getSceneEnd = (index) => {
  if (index >= sceneDefinitions.length - 1) return clampScroll(Number.POSITIVE_INFINITY);
  return Math.max(getSceneStart(index), getSceneStart(index + 1) - window.innerHeight);
};

// 씬 내부 스크롤 여유(콘텐츠가 뷰포트를 넘는 양). 뷰포트를 조금만 넘는 씬(top의 로고 띠,
// final의 푸터 등)은 사실상 한 화면짜리로 취급하고, 진짜 여러 화면 분량인 씬만 tall로 본다.
const getSceneOverflow = (index) => Math.max(0, getSceneEnd(index) - getSceneStart(index));
const isTallScene = (index) => getSceneOverflow(index) >= window.innerHeight * 0.5;

const getSceneIndex = (position = window.scrollY) => {
  let activeIndex = 0;
  sceneDefinitions.forEach((scene, index) => {
    if (position >= getSceneStart(index) - 2) activeIndex = index;
  });
  return activeIndex;
};

const stopSmoothScroll = ({ sync = true } = {}) => {
  if (scrollFrame) window.cancelAnimationFrame(scrollFrame);
  scrollFrame = undefined;
  scrollEngineRunning = false;
  rootElement.style.scrollBehavior = previousScrollBehavior;

  if (sync) {
    renderedScroll = window.scrollY;
    targetScroll = window.scrollY;
  }
};

const renderSmoothScroll = (now) => {
  const elapsed = Math.min(34, Math.max(1, now - lastFrameAt));
  const smoothing = 1 - Math.exp(-elapsed / 66);
  renderedScroll += (targetScroll - renderedScroll) * smoothing;

  if (Math.abs(targetScroll - renderedScroll) < 0.12) renderedScroll = targetScroll;

  window.scrollTo(0, renderedScroll);
  updateActiveNavigation();
  lastFrameAt = now;

  if (renderedScroll !== targetScroll) {
    scrollFrame = window.requestAnimationFrame(renderSmoothScroll);
    return;
  }

  stopSmoothScroll({ sync: false });
};

const startSmoothScroll = () => {
  if (scrollEngineRunning || sceneTransitionRunning) return;
  previousScrollBehavior = rootElement.style.scrollBehavior;
  rootElement.style.scrollBehavior = 'auto';
  renderedScroll = window.scrollY;
  lastFrameAt = performance.now();
  scrollEngineRunning = true;
  scrollFrame = window.requestAnimationFrame(renderSmoothScroll);
};

const setImmediateScroll = (position) => {
  const preservedBehavior = rootElement.style.scrollBehavior;
  rootElement.style.scrollBehavior = 'auto';
  window.scrollTo(0, position);
  rootElement.style.scrollBehavior = preservedBehavior;
  renderedScroll = position;
  targetScroll = position;
  updateHeaderState();
  updateActiveNavigation();
};

const transitionToScene = async (targetIndex, requestedDirection) => {
  if (
    sceneTransitionRunning
    || targetIndex < 0
    || targetIndex >= sceneDefinitions.length
  ) return;

  const currentIndex = getSceneIndex();
  if (targetIndex === currentIndex) {
    targetScroll = getSceneStart(targetIndex);
    startSmoothScroll();
    return;
  }

  const direction = requestedDirection ?? Math.sign(targetIndex - currentIndex);
  const leavingClass = direction > 0 ? 'is-scene-leaving-forward' : 'is-scene-leaving-backward';
  const enteringClass = direction > 0 ? 'is-scene-entering-forward' : 'is-scene-entering-backward';
  const leavingElements = sceneDefinitions[currentIndex].elements;
  const enteringElements = sceneDefinitions[targetIndex].elements;
  // 아래로 진입하면 항상 씬 맨 위에 착지한다. 위로 되돌아올 때는 진짜 여러 화면 분량인 씬만
  // 바닥(getSceneEnd)에 착지해 콘텐츠를 위로 훑게 하고, 뷰포트를 조금만 넘는 씬은 맨 위에
  // 딱 맞춰 착지시킨다. (top으로 복귀할 때 로고 띠 프레이밍(139px)에 걸쳤다가 다시 슥
  // 올라가던 현상을 없앤다.)
  const landingPosition = direction > 0 || !isTallScene(targetIndex)
    ? getSceneStart(targetIndex)
    : getSceneEnd(targetIndex);
  // tall 씬은 요소 중심이 뷰포트 밖(아래)이라, 중심 기준 scale 리빌이 화면에 보이는
  // 상단(kicker)을 세로로 밀어낸다. 스케일 피벗을 화면에 고정된 지점으로 옮겨 상단이
  // 전혀 움직이지 않게 한다: 아래로 진입 시 콘텐츠 상단(= padding-top = kicker 라인)에
  // 정확히 맞추고, 위로 복귀 시엔 하단(50% 100%)에 맞춘다.
  // 짧은 씬은 중심 기준이 자연스러워 그대로 둔다(빈 문자열 = CSS 기본 50% 50%).
  const enteringIsTall = isTallScene(targetIndex);

  sceneTransitionRunning = true;
  resetBoundaryGesture();
  stopSmoothScroll();
  document.body.classList.add('is-scene-fading');
  leavingElements.forEach((element) => element.classList.add(leavingClass));

  await wait(330);

  enteringElements.forEach((element) => {
    element.style.transformOrigin = enteringIsTall
      ? (direction > 0 ? `50% ${parseFloat(getComputedStyle(element).paddingTop) || 0}px` : '50% 100%')
      : '';
    element.classList.add(enteringClass);
  });
  setImmediateScroll(landingPosition);
  leavingElements.forEach((element) => element.classList.remove(leavingClass));
  document.body.classList.remove('is-scene-fading');
  document.body.classList.add('is-scene-revealing');

  await new Promise((resolve) => window.requestAnimationFrame(() => window.requestAnimationFrame(resolve)));
  enteringElements.forEach((element) => element.classList.remove(enteringClass));

  await wait(580);
  document.body.classList.remove('is-scene-revealing');
  // 다음에 이 씬이 떠날 때(leaving scale)는 중심 기준으로 돌아가도록 인라인 피벗 해제.
  // 이 시점엔 scale이 1(항등)이라 해제해도 시각적 변화가 없다.
  enteringElements.forEach((element) => { element.style.transformOrigin = ''; });
  sceneTransitionRunning = false;
};

const normalizeWheelDelta = (event) => {
  if (event.deltaMode === WheelEvent.DOM_DELTA_LINE) return event.deltaY * 18;
  if (event.deltaMode === WheelEvent.DOM_DELTA_PAGE) return event.deltaY * window.innerHeight;
  return event.deltaY;
};

const handleSmoothWheel = (event) => {
  if (!smoothScrollMedia.matches || event.ctrlKey || Math.abs(event.deltaX) > Math.abs(event.deltaY)) return;

  event.preventDefault();
  if (sceneTransitionRunning) return;

  const now = performance.now();
  const rawDelta = normalizeWheelDelta(event);
  const limitedDelta = Math.max(-220, Math.min(220, rawDelta));
  const direction = Math.sign(limitedDelta);
  if (direction === 0) return;

  const wheelGap = now - lastWheelAt;
  const startsNewGesture = wheelGap > 96;
  if (startsNewGesture) {
    if (scrollEngineRunning) stopSmoothScroll();
    renderedScroll = window.scrollY;
    targetScroll = window.scrollY;
  }

  const sceneIndex = getSceneIndex(targetScroll);
  const sceneStart = getSceneStart(sceneIndex);
  const sceneEnd = getSceneEnd(sceneIndex);
  const boundaryTolerance = Math.min(64, window.innerHeight * 0.08);
  const atStart = targetScroll <= sceneStart + 1 && renderedScroll <= sceneStart + boundaryTolerance;
  const atEnd = targetScroll >= sceneEnd - 1 && renderedScroll >= sceneEnd - boundaryTolerance;
  const reachesBoundary = (
    (direction > 0 && atEnd && sceneIndex < sceneDefinitions.length - 1)
    || (direction < 0 && atStart && sceneIndex > 0)
  );

  if (boundaryGestureArmed) {
    const continuesPastBoundary = direction === boundaryDirection && (
      (direction > 0 && atEnd)
      || (direction < 0 && atStart)
    );
    const magnitude = Math.abs(limitedDelta);
    const renewedTrackpadFlick = (
      boundaryHasSettled
      && magnitude >= 8
      && magnitude >= boundaryLastDelta * 1.55
      && magnitude - boundaryLastDelta >= 3
    );

    if (continuesPastBoundary && (startsNewGesture || renewedTrackpadFlick)) {
      resetBoundaryGesture();
      lastWheelAt = now;
      transitionToScene(sceneIndex + direction, direction);
      return;
    }

    if (continuesPastBoundary) {
      boundaryPeakDelta = Math.max(boundaryPeakDelta, magnitude);
      boundaryHasSettled ||= magnitude <= Math.max(8, boundaryPeakDelta * 0.1);
      boundaryLastDelta = magnitude;
      lastWheelAt = now;
      return;
    }

    resetBoundaryGesture();
  }

  if (reachesBoundary) {
    armBoundaryGesture(direction, Math.abs(limitedDelta));
    lastWheelAt = now;
    return;
  }

  targetScroll = Math.min(
    sceneEnd,
    Math.max(sceneStart, targetScroll + limitedDelta * 0.9),
  );
  lastWheelAt = now;

  if (
    (direction > 0 && targetScroll >= sceneEnd - 1 && sceneIndex < sceneDefinitions.length - 1)
    || (direction < 0 && targetScroll <= sceneStart + 1 && sceneIndex > 0)
  ) {
    armBoundaryGesture(direction, Math.abs(limitedDelta));
  }

  startSmoothScroll();
};

window.addEventListener('wheel', handleSmoothWheel, { passive: false });

window.addEventListener('scroll', () => {
  if (scrollEngineRunning || sceneTransitionRunning) return;
  renderedScroll = window.scrollY;
  targetScroll = window.scrollY;
}, { passive: true });

document.querySelectorAll('a[href^="#"]').forEach((link) => {
  const targetId = link.getAttribute('href').slice(1) || 'top';
  const targetIndex = sceneDefinitions.findIndex((scene) => scene.id === targetId);
  if (targetIndex < 0) return;

  link.addEventListener('click', (event) => {
    if (!smoothScrollMedia.matches) return;
    event.preventDefault();
    const currentIndex = getSceneIndex();
    transitionToScene(targetIndex, Math.sign(targetIndex - currentIndex));
    window.history.replaceState(null, '', `#${targetId}`);
  });
});

['touchstart', 'pointerdown'].forEach((eventName) => {
  window.addEventListener(eventName, () => {
    if (!sceneTransitionRunning) stopSmoothScroll();
  }, { passive: true });
});

window.addEventListener('keydown', () => {
  if (!sceneTransitionRunning) stopSmoothScroll();
}, { passive: true });

window.addEventListener('resize', () => {
  stopSmoothScroll();
  resetBoundaryGesture();
  syncNativeScrollBehavior();
  targetScroll = clampScroll(targetScroll);
  renderedScroll = window.scrollY;
  updateActiveNavigation();
});

// 데스크톱↔모바일/모션축소 경계를 오갈 때 CSS 스무스 스크롤 on/off를 다시 맞춘다.
smoothScrollMedia.addEventListener('change', () => {
  stopSmoothScroll();
  resetBoundaryGesture();
  syncNativeScrollBehavior();
});
