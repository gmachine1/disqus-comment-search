import com.gmachine1729.disqus_comment_search._
import org.scalatra._
import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    context.mount(new DisqusCommentSearchServlet, "/*")
  }
}
