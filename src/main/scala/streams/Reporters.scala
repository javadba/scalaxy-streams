package scalaxy.streams

trait Reporters {
  val global: scala.reflect.api.Universe
  import global._

  def info(pos: Position, msg: String, force: Boolean = true): Unit
  def warning(pos: Position, msg: String): Unit
  def error(pos: Position, msg: String): Unit
}
