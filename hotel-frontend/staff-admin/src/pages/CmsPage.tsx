import { CmsEntryGrid } from '../features/cms/CmsEntryGrid'
export function CmsPage() {
  return (
    <section>
      <div className="page-heading">
        <div><p className="kicker">PUBLIC WEBSITE</p><h2>官网内容管理</h2><p>选择内容类型开始编辑。页面内容需要 Publish 后才会出现在官网。</p></div>
      </div>
      <div className="workflow-note"><strong>推荐流程</strong><span>先上传 Media → 编辑 Page/Section 或 Promotion → Preview 检查 → Publish。</span></div>
      <CmsEntryGrid />
    </section>
  )
}
