import { useState } from 'react'
import Markdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import remarkGfm from 'remark-gfm'

/**
 * 统一的 Markdown 渲染:题干、参考答案、AI 点评共用。
 * 样式在 index.css 的 .markdown-body 里统一维护,避免各处重复写类名。
 * 题面里的配图支持点击放大,方便看清图示。
 */
function MarkdownContent({ content, className = '' }: { content: string; className?: string }) {
  const [preview, setPreview] = useState<string | null>(null)
  if (!content) return null

  return (
    <>
      <div className={`markdown-body ${className}`}>
        <Markdown
          remarkPlugins={[remarkGfm]}
          rehypePlugins={[rehypeHighlight]}
          components={{
            img: ({ src, alt, title }) => (
              <img
                src={src}
                alt={alt ?? ''}
                title={title ?? undefined}
                loading="lazy"
                className="markdown-zoomable"
                onClick={() => {
                  if (src) setPreview(src)
                }}
              />
            ),
          }}
        >
          {content}
        </Markdown>
      </div>

      {preview && (
        <div
          className="anim-fade-in fixed inset-0 z-50 flex cursor-zoom-out items-center justify-center bg-slate-900/80 p-6"
          onClick={() => setPreview(null)}
        >
          <img
            src={preview}
            alt=""
            className="anim-pop-in max-h-full max-w-full rounded-lg bg-white object-contain shadow-2xl"
          />
        </div>
      )}
    </>
  )
}

export default MarkdownContent
