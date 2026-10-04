import { Link } from 'react-router-dom'
const entries = [
  { label: '页面与区块', description: '编辑官网页面、Section、预览并发布版本。', path: '/cms/pages' },
  { label: '媒体库', description: '上传图片、视频或 3D 模型，查看引用前素材。', path: '/cms/media' },
  { label: '促销内容', description: '维护营销文案、时间、图片和发布状态。', path: '/cms/promotions' },
  { label: '官网导航', description: '维护菜单顺序、跳转目标与打开方式。', path: '/cms/navigation' },
  { label: '酒店位置', description: '编辑名称、地址、坐标与公开联系方式。', path: '/cms/location' },
  { label: '3D 场景', description: '管理公开预览与内部交互场景元数据。', path: '/cms/scenes' },
]

export function CmsEntryGrid() {
  return (
    <div className="entry-grid">
      {entries.map((entry) => (
        <Link key={entry.label} className="entry-card" to={entry.path}>
          <span>CMS</span>
          <h3>{entry.label}</h3>
          <p>{entry.description}</p>
          <strong>进入管理 →</strong>
        </Link>
      ))}
    </div>
  )
}


