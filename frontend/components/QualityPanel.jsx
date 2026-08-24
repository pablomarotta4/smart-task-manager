const asPercent = (value) => `${Math.round(value * 100)}%`;

export default function QualityPanel({ quality, model, revisionCount }) {
  const metrics = [
    ["Unique titles", asPercent(quality.metrics.unique_title_ratio)],
    ["Description coverage", asPercent(quality.metrics.description_coverage)],
    ["Acceptance criteria", asPercent(quality.metrics.acceptance_criteria_coverage)],
    ["Highest title overlap", asPercent(quality.metrics.max_title_similarity)],
  ];

  return (
    <aside
      className={`quality-panel ${quality.passed ? "quality-pass" : "quality-warning"}`}
      aria-label="Plan quality"
      role="status"
    >
      <div className="quality-heading">
        <p className="section-index">Quality check</p>
        <span className="quality-dot" aria-hidden="true" />
      </div>
      <h3 id="quality-title">{quality.passed ? "Ready to review" : "Needs attention"}</h3>
      <div
        className="score-ring"
        role="progressbar"
        aria-label="Quality score"
        aria-valuemin="0"
        aria-valuemax="100"
        aria-valuenow={quality.score}
        style={{ "--quality-score": `${quality.score * 3.6}deg` }}
      >
        <strong>{quality.score} / 100</strong>
        <span>plan score</span>
      </div>

      <dl className="quality-metrics">
        {metrics.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>

      {quality.issues.length > 0 ? (
        <div className="quality-issues">
          <h4>What to improve</h4>
          <ul>
            {quality.issues.map((issue) => (
              <li key={`${issue.code}-${issue.message}`}>{issue.message}</li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="quality-note">
          The draft passed the coverage, actionability, and repetition checks.
        </p>
      )}

      <p className="model-note">
        {model} · {revisionCount === 1 ? "1 AI revision" : "No AI revision"}
      </p>
    </aside>
  );
}
