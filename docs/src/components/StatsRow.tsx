import { useEffect, useMemo, useRef, useState } from 'react';

type Stat = { num: string; unit?: string; label: string };
type Props = { stats: Stat[] };

type Parsed = { target: number; prefix: string; suffix: string };

function parseTarget(num: string): Parsed | null {
  const m = num.match(/^(\D*)(\d+(?:\.\d+)?)(\D*)$/);
  if (!m) return null;
  return { prefix: m[1], target: parseFloat(m[2]), suffix: m[3] };
}

function AnimatedNumber({ raw, active }: { raw: string; active: boolean }) {
  const parsed = useMemo<Parsed | null>(() => parseTarget(raw), [raw]);
  const [value, setValue] = useState(0);

  useEffect(() => {
    if (!active || !parsed) return;
    const duration = 900;
    const start = performance.now();
    const target = parsed.target;
    let raf = 0;
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - t, 3);
      setValue(target * eased);
      if (t < 1) raf = requestAnimationFrame(tick);
      else setValue(target);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [active, parsed]);

  if (!parsed) return <>{raw}</>;
  const display = parsed.target % 1 === 0
    ? Math.round(value).toString()
    : value.toFixed(1);
  return <>{parsed.prefix}{display}{parsed.suffix}</>;
}

export default function StatsRow({ stats }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(false);

  useEffect(() => {
    if (!ref.current || active) return;
    const el = ref.current;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setActive(true);
          observer.disconnect();
        }
      },
      { threshold: 0.4 },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [active]);

  return (
    <div className="stats-row" ref={ref}>
      {stats.map((s, i) => (
        <div className={'stat' + (active ? ' stat-in' : '')} key={i} style={{ transitionDelay: `${i * 80}ms` }}>
          <div className="stat-num">
            <AnimatedNumber raw={s.num} active={active} />
            {s.unit ? <span className="stat-unit"> {s.unit}</span> : null}
          </div>
          <div className="stat-label">{s.label}</div>
        </div>
      ))}
    </div>
  );
}
