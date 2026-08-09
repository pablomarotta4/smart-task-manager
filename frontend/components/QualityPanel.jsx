const asPercent = (value) => `${Math.round(value * 100)}%`;

export default function QualityPanel({ quality, model, revisionCount }) {
  const metrics = [
    ["Unique titles", asPercent(quality.metrics.unique_title_ratio)],
    ["Description coverage", asPercent(quality.metrics.description_coverage)],
    ["Acceptance criteria", asPercent(quality.metrics.acceptance_criteria_coverage)],
    ["Highest title overlap", asPercent(quality.metrics.max_title_similarity)],
  ];

  return (
    <aside aria-labelledby="quality-title">
      <p>Quality check</p>
      <h3 id="quality-title">{quality.passed ? "Ready to review" : "Needs attention"}</h3>
      <p aria-label={`Quality score ${quality.score} out of 100`}>
        <strong>{quality.score} / 100</strong>
      </p>

      <dl>
        {metrics.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>

      {quality.issues.length > 0 ? (
        <div>
          <h4>What to improve</h4>
          <ul>
            {quality.issues.map((issue) => (
              <li key={`${issue.code}-${issue.message}`}>{issue.message}</li>
            ))}
          </ul>
        </div>
      ) : (
        <p>The draft passed the coverage, actionability, and repetition checks.</p>
      )}

      <p>
        {model} · {revisionCount === 1 ? "1 AI revision" : "No AI revision"}
      </p>
    </aside>
  );
}
