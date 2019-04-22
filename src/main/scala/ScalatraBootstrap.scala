import org.scalatra.LifeCycle
import javax.servlet.ServletContext
import com.gmachine1729.disqus_comment_search.DisqusCommentSearchServlet
import com.gmachine1729.disqus_comment_search.db.DatabaseInit

class ScalatraBootstrap extends LifeCycle with DatabaseInit {
  override def init(context: ServletContext) {
    configureDb()
    context mount (new DisqusCommentSearchServlet, "/*")
  }

  override def destroy(context:ServletContext) {
    closeDbConnection()
  }
}
