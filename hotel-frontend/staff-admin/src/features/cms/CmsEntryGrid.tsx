import { Link } from 'react-router-dom'
const entries = [{ label: 'Pages', path: '/cms/pages', ready: true }, { label: 'Promotions', path: '/cms/promotions', ready: true }, { label: 'Media', path: '/cms/media', ready: true }, { label: 'Navigation', path: '/cms/navigation', ready: true }, { label: 'Location', path: '/cms/location', ready: true }, { label: 'Scenes', path: '/cms/scenes', ready: true }]
export function CmsEntryGrid() { return <div className="entry-grid">{entries.map((entry) => <section key={entry.label} className="entry-card"><h3>{entry.label}</h3>{entry.ready ? <Link to={entry.path!}>进入管理</Link> : <p>后续阶段实现。</p>}</section>)}</div> }


