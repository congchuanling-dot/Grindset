import Markdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import remarkGfm from 'remark-gfm'

/**
 * 统一的 Markdown 渲染:题干、参考答案、AI 点评共用。
 * 样式在 index.css 的 .markdown-body 里统一维护,避免各处重复写类名。
 */
function MarkdownContent({ content, className = '' }: { content: string; className?: string }) {
  if (!content) return null
  return (
    <div className={`markdown-body ${className}`}>
      <Markdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
        {content}
      </Markdown>
    </div>
  )
}

export default MarkdownContent
