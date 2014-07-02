package scalaxy.streams

private[streams] trait ToCollectionOps
    extends StreamComponents
    with ListBufferSinks
    with ArrayBuilderSinks
{
  val global: scala.reflect.api.Universe
  import global._

  object SomeToCollectionOp {
    def unapply(tree: Tree): Option[(Tree, ToCollectionOp)] = Option(tree) collect {
      case q"$target.toList" =>
        (target, ToListOp)

      case q"$target.toArray[${_}](${_})" =>
        (target, ToArrayOp)
    }
  }

  class ToCollectionOp(name: String, sink: StreamSink) extends PassThroughStreamOp {
    override def describe = Some(name)
    override def sinkOption = Some(sink)
    override def lambdaCount = 0
  }

  case object ToListOp extends ToCollectionOp("toList", ListBufferSink)

  case object ToArrayOp extends ToCollectionOp("toArray", ArrayBuilderSink)
}
