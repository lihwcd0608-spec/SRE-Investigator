export function ReviewEmptyState({
  hasRepo,
  hasBase,
  hasHead,
  sameBranch,
}: {
  hasRepo: boolean;
  hasBase: boolean;
  hasHead: boolean;
  sameBranch: boolean;
}) {
  const step = !hasRepo ? 1 : !hasBase ? 2 : !hasHead || sameBranch ? 3 : 3;
  return (
    <div className="review-empty">
      <div className="review-empty-art" aria-hidden="true">
        <svg viewBox="0 0 420 220" fill="none">
          <rect x="18" y="28" width="172" height="164" rx="14" stroke="currentColor" strokeOpacity="0.28" />
          <rect x="230" y="28" width="172" height="164" rx="14" stroke="currentColor" strokeOpacity="0.42" />
          <path d="M190 110h40" stroke="currentColor" strokeOpacity="0.45" />
          <circle cx="210" cy="110" r="10" stroke="currentColor" strokeOpacity="0.55" />
          <path d="M205 110h10M210 105v10" stroke="currentColor" strokeOpacity="0.7" />
          <rect x="36" y="48" width="88" height="8" rx="4" fill="currentColor" fillOpacity="0.16" />
          <rect x="36" y="68" width="136" height="6" rx="3" fill="currentColor" fillOpacity="0.1" />
          <rect x="36" y="84" width="118" height="6" rx="3" fill="currentColor" fillOpacity="0.1" />
          <rect x="36" y="100" width="128" height="6" rx="3" fill="currentColor" fillOpacity="0.08" />
          <rect x="248" y="48" width="88" height="8" rx="4" fill="currentColor" fillOpacity="0.22" />
          <rect x="248" y="68" width="136" height="6" rx="3" fill="currentColor" fillOpacity="0.12" />
          <rect x="248" y="84" width="118" height="6" rx="3" fill="currentColor" fillOpacity="0.18" />
          <rect x="248" y="100" width="128" height="6" rx="3" fill="currentColor" fillOpacity="0.1" />
          <rect x="248" y="124" width="96" height="18" rx="6" fill="currentColor" fillOpacity="0.14" />
        </svg>
      </div>
      <div className="review-empty-copy">
        <p className="kicker">代码对照</p>
        <h3>{sameBranch ? "请换一个不同的对比分支" : hasHead ? "正在等待对比结果" : "先选对比分支，再看左右对照"}</h3>
        <p>基准保持主干（release / main）。选好对比分支后，这里会按 IDEA 方式同时展示两侧代码。</p>
        <ol className="review-empty-steps">
          <li className={step >= 1 ? "done" : ""}>选择仓库</li>
          <li className={step >= 2 && hasBase ? "done" : step === 2 ? "current" : ""}>确认基准分支</li>
          <li className={!hasHead || sameBranch ? "current" : "done"}>选择对比分支</li>
        </ol>
      </div>
    </div>
  );
}
