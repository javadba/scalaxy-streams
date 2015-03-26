package scalaxy.streams
package test

import org.junit._
import org.junit.Assert._

import scala.collection.mutable.ListBuffer
import scala.tools.reflect.ToolBox
import scala.tools.reflect.FrontEnd

case class CompilerMessages(
  infos: List[String] = Nil,
  warnings: List[String] = Nil,
  errors: List[String] = Nil)

trait StreamComponentsTestBase extends Utils with ConsoleReporters
{
  val global = scala.reflect.runtime.universe
  val commonOptions = "-usejavacp "
  val optOptions = "-optimise -Yclosure-elim -Yinline "//-Ybackend:GenBCode"
  import scala.reflect.runtime.currentMirror

  private[this] lazy val toolbox = currentMirror.mkToolBox(options = commonOptions)

  def typecheck(t: global.Tree): global.Tree =
    toolbox.typecheck(t.asInstanceOf[toolbox.u.Tree]).asInstanceOf[global.Tree]

  def compileOpt(source: String) = compile(source, opt = true)
  def compileFast(source: String) = compile(source, opt = false)

  private[this] def threadLocal[A](a: => A) =
    new ThreadLocal[A] {
      override def initialValue() = a
    }

  private[this] val infosBuilder = threadLocal(ListBuffer[String]())
  private[this] val warningsBuilder = threadLocal(ListBuffer[String]())
  private[this] val errorsBuilder = threadLocal(ListBuffer[String]())

  private[this] val frontEnd = new FrontEnd {
    override def display(info: Info) {
      val builder: ListBuffer[String] = info.severity match {
        case INFO => infosBuilder.get
        case WARNING => warningsBuilder.get
        case ERROR => errorsBuilder.get
      }

      builder += info.msg
    }
    override def interactive() {}
  }

  private[this] def makeToolbox(opt: Boolean) =
    currentMirror.mkToolBox(
      frontEnd,
      if (opt) commonOptions + optOptions else commonOptions)

  private[this] val optToolbox = threadLocal(makeToolbox(true))
  private[this] val normalToolbox = threadLocal(makeToolbox(false))

  private[this] def compile(source: String, opt: Boolean = false): (() => Any, CompilerMessages) = {
    infosBuilder.get.clear()
    warningsBuilder.get.clear()
    errorsBuilder.get.clear()
    
    val toolbox = (if (opt) optToolbox else normalToolbox).get
    import toolbox.u._

    try {
      val tree = toolbox.parse(source);
      val compilation = toolbox.compile(tree)

      (
        compilation,
        CompilerMessages(
          infos = infosBuilder.get.result,
          warnings = warningsBuilder.get.result,
          errors = errorsBuilder.get.result)
      )
    } catch { case ex: Throwable =>
      throw new RuntimeException(s"Failed to compile:\n$source", ex)
    }
  }

  def optimizedCode(source: String, strategy: OptimizationStrategy): String = {
    val src = s"""{
      import ${strategy.fullName}
      scalaxy.streams.optimize {
        $source
      }
    }"""
    // println(src)
    src
  }

  def testMessages(source: String, expectedMessages: CompilerMessages,
                   expectWarningRegexp: Option[List[String]] = None)
                  (implicit strategy: OptimizationStrategy) {

    val actualMessages = try {
      assertMacroCompilesToSameValue(
        source,
        strategy = strategy)
    } catch { case ex: Throwable =>
      ex.printStackTrace()
      if (ex.getCause != null)
        ex.getCause.printStackTrace()
      throw new RuntimeException(ex)
    }

    if (expectedMessages.infos != actualMessages.infos) {
      actualMessages.infos.foreach(println)
      assertEquals(expectedMessages.infos, actualMessages.infos)
    }
    expectWarningRegexp match {
      case Some(rxs) =>
        val warnings = actualMessages.warnings
        assert(expectedMessages.warnings.isEmpty)
        assertEquals(warnings.toString,
          rxs.size, warnings.size)
        for ((rx, warning) <- rxs.zip(warnings)) {
          assertTrue(s"Expected '$rx', got '$warning'\n(full warnings: ${actualMessages.warnings})",
            warning.matches(rx))
        }
        // assertEquals(actualMessages.warnings.toString,
        //   count, actualMessages.warnings.size)

      case None =>
        assertEquals(
          expectedMessages.warnings.toSet,
          actualMessages.warnings.toSet)
    }
    assertEquals(expectedMessages.errors, actualMessages.errors)
  }

  val pluginCompiler = threadLocal {
    StreamsCompiler.makeCompiler(StreamsCompiler.consoleReportGetter)
  }

  def assertPluginCompilesSnippetFine(source: String) {
    val sourceFile = {
      import java.io._

      val f = File.createTempFile("test-", ".scala")
      val out = new PrintStream(f)
      out.println(s"object Test { def run = { $source } }")
      out.close()

      f
    }

    val args = Array(sourceFile.toString)
    pluginCompiler.get()(args)
  }

  case class Exceptional(exceptionString: String)
  def eval(f: () => Any) =
    try { f() }
    catch {
      case ex: java.lang.reflect.InvocationTargetException =>
        Exceptional(ex.getCause.toString)
    }

  def assertMacroCompilesToSameValue(source: String, strategy: OptimizationStrategy): CompilerMessages = {
    val (unoptimized, unoptimizedMessages) = compileFast(source)
    val (optimized, optimizedMessages) = compileFast(optimizedCode(source, strategy))

    val unopt = eval(unoptimized)
    val opt = eval(optimized)
    assertEqualValues(source + "\n" + optimizedMessages, unopt, opt)

    // assertEquals("Unexpected messages during unoptimized compilation",
    //   CompilerMessages(), unoptimizedMessages)

    optimizedMessages
  }

  def assertEqualValues(message: String, expected: Any, actual: Any) = {
    (expected, actual) match {
      case (expected: Array[Int], actual: Array[Int]) =>
        assertArrayEquals(message, expected, actual)
      case (expected: Array[Short], actual: Array[Short]) =>
        assertArrayEquals(message, expected, actual)
      case (expected: Array[Byte], actual: Array[Byte]) =>
        assertArrayEquals(message, expected, actual)
      case (expected: Array[Char], actual: Array[Char]) =>
        assertArrayEquals(message, expected, actual)
      case (expected: Array[Float], actual: Array[Float]) =>
        assertArrayEquals(message, expected, actual, 0)
      case (expected: Array[Double], actual: Array[Double]) =>
        assertArrayEquals(message, expected, actual, 0)
      case (expected: Array[Boolean], actual: Array[Boolean]) =>
        assertArrayEquals(message, expected.map(_.toString: AnyRef), actual.map(_.toString: AnyRef))
      case (expected, actual) =>
        assertEquals(message, expected, actual)
        assertEquals(message + " (different classes)",
          Option(expected).map(_.getClass).orNull,
          Option(actual).map(_.getClass).orNull)
    }
  }
}
