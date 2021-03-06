[![Maven Central](https://img.shields.io/maven-central/v/com.nativelibs4java/scalaxy-streams_2.11.svg)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.nativelibs4java%22%20AND%20a%3A%22scalaxy-streams_2.11%22) [![LICENSE](https://img.shields.io/badge/license-BSD-yellowgreen.svg)](./LICENSE) [![Build Status](https://travis-ci.org/nativelibs4java/scalaxy-streams.svg?branch=master)](https://travis-ci.org/nativelibs4java/scalaxy-streams) [![CircleCI branch](https://img.shields.io/circleci/project/BrightFlair/PHP.Gt/master.svg?label=scala-opt)](https://circleci.com/gh/nativelibs4java/scalaxy-streams) [![Join the chat at https://gitter.im/nativelibs4java/Scalaxy](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/nativelibs4java/Scalaxy?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) 
 
# Scalaxy/Streams

Latest release: 0.3.4 (2014-11-04, see [Changelog](#changelog))

Quick links:
* [Slides of Scalaxy/Streams talk @ Scala.io 2014 Paris](https://docs.google.com/presentation/d/100NlsLI79I1ljseCnY7r0IX3AUFKR7BqykOKdm4PRMw/edit#slide=id.p)
* [Usage with Sbt](#usage-with-sbt)
* [Usage with Maven](#usage-with-maven)
* [Usage with Eclipse](#usage-with-eclipse)
* [Optimization Strategies](#optimization-strategies)
* [TODO](#todo)
* [Why is this not part of Scala](#why-is-this-not-part-of-scala)

Scalaxy/Streams makes your Scala 2.11.x collections code faster (official heir to [ScalaCL](https://code.google.com/p/scalacl/) and [Scalaxy/Loops](https://github.com/ochafik/Scalaxy/tree/master/Loops), by same author):

* Fuses collection streams down to while loops (see [some examples](https://github.com/ochafik/Scalaxy/blob/master/Streams/src/test/scala/IntegrationTests.scala#L55))
* Avoids many unnecessary tuples (for instance, those introduced by `zipWithIndex`).
* "Safe by default" (optimizations preserve Scala semantics, with side-effect analysis).
* Available as a compiler plugin (whole project) or as a macro (surgical strikes). No runtime deps.
* [BSD-licensed](./LICENSE)

```scala
// For instance, given the following array:
val array = Array(1, 2, 3, 4)

// The following for comprehension:
for ((item, i) <- array.zipWithIndex; if item % 2 == 0) {
  println(s"array[$i] = $item")
}

// Is desugared by Scala to (slightly simplified):
array.zipWithIndex.withFilter((pair: (Int, Int)) => pair match {
  case (item: Int, i: Int) => true
  case _ => false
}).withFilter((pair: (Int, Int)) => pair match {
  case (item, i) =>
    item % 2 == 0
}).foreach((pair: (Int, Int)) => pair match {
  case (item, i) =>
    println(s"array[$i] = $item")
})
// Which will perform as badly and generate as many class files as you might fear.

// Scalaxy/Streams will simply rewrite it to something like:
val array = Array(1, 2, 3, 4)
var i = 0;
val length = array.length
while (i < length) {
  val item = array(i)
  if (item % 2 == 0) {
    println(s"array[$i] = $item")
  }
}
```

**Caveat**: Scalaxy/Streams is still a young project and relies on experimental Scala features (macros), so:

* Be careful about using it in production yet. In particular, please test your code thoroughly and make sure your tests aren't compiled with Scalaxy/Streams, maybe with something like this in your `build.sbt`:

        scalacOptions in Test += "-Xplugin-disable:scalaxy-streams"

* Spend some time analyzing the verbose or very-verbose output (`SCALAXY_STREAMS_VERY_VERBOSE=1 sbt ...`) to make sure you understand what the plugin / macro do.

* If you run micro-benchmarks, don't forget to use the following scalac optimization flags (also consider `-Ybackend:GenBCode`):

        -optimise -Yclosure-elim -Yinline

# Scope

Scalaxy/Streams rewrites streams with the following components:

* Stream sources:
  * inline `Range`,
  * `Option` (with special case for explicit `Option(x)`),
  * explicit `Seq(a, b, ...)` (array-based rewrite),
  * `List` (with special array-based rewrite for explicit `List(a, b, ...)`)
  * `Array` & `ArrayOps`,
  * `js.Array` & `js.ArrayOps` (JavaScript Array in [Scala.js](www.scala-js.org))
  * `Iterable`
  * Added `js.Array` & `js.ArrayOps`
* Stream operations:
  * `filter`, `filterNot`, `withFilter`,
  * `map`,
  * `flatMap` (with or without nested streams),
  * `flatten` (fenced under `experimental` switch),
  * `count`,
  * `exists`, `forall`,
  * `find`
  * `take`, `drop`, `takeWhile`, `dropWhile` (except on `Range` & `Option`)
  * `zipWithIndex`
  * `sum`, `product`
  * `toList`, `toArray`, `toVector`, `toSet`
  * `isEmpty`, `nonEmpty`
  * `Option.get`, `getOrElse`, `isDefined`, `orNull`, `orElse`
  * `mkString`
* Stream sinks
  * `ArrayBuilder`
  * `Option`
  * `Set`
  * `Vector`
  * `ListBuffer`
  * `CanBuildFrom` (as collected from `map`, `flatMap` and other ops, with special cases for `ArrayBuilder` / `js.ArrayBuilder`)

The output type of each optimized stream is always the same as the original, but when nested streams are encountered in `flatMap` operations many intermediate outputs can typically be skipped, saving up on memory usage and execution time.

Note: known bugs are usually accommodated in `Strategies.hasKnownLimitationOrBug` when possible.

# Usage

You can either use Scalaxy/Streams's compiler plugin to compile your whole project, or use its `optimize` macro to choose specific blocks of code to optimize.

You can always disable loop optimizations by recompiling with the environment variable `SCALAXY_STREAMS_OPTIMIZE=0` or the System property `scalaxy.streams.optimize=false` set:
```
SCALAXY_STREAMS_OPTIMIZE=0 sbt clean compile ...
```
Or if you're not using sbt:
```
scalac -J-Dscalaxy.streams.optimize=false ...
```

## Usage with Sbt

If you're using `sbt` 0.13.0+, just put the following lines in `build.sbt`:

* To use the macro (manually decide which parts of your code are optimized):

  ```scala
  scalaVersion := "2.11.6"

  // Dependency at compilation-time only (not at runtime).
  libraryDependencies += "com.nativelibs4java" %% "scalaxy-streams" % "0.3.4" % "provided"

  // Like to live on the wild side? Try the snapshot out:
  // libraryDependencies += "com.nativelibs4java" %% "scalaxy-streams" % "0.4-SNAPSHOT" % "provided"

  // resolvers += Resolver.sonatypeRepo("snapshots")  
  ```

  And wrap some code with the `optimize` macro:
  ```scala
  import scalaxy.streams.optimize
  optimize {
      for (i <- 0 until n; j <- i until n; if (i + j)  % 2 == 0) {
        ...
      }
  }
  ```

* To use the compiler plugin (optimizes all of your code):

  ```scala
  scalaVersion := "2.11.6"

  scalacOptions += "-Xplugin-require:scalaxy-streams"

  scalacOptions in Test ~= (_ filterNot (_ == "-Xplugin-require:scalaxy-streams"))
  
  scalacOptions in Test += "-Xplugin-disable:scalaxy-streams"

  autoCompilerPlugins := true

  addCompilerPlugin("com.nativelibs4java" %% "scalaxy-streams" % "0.3.4")

  // Like to live on the wild side? Try the snapshot out:
  // addCompilerPlugin("com.nativelibs4java" %% "scalaxy-streams" % "0.4-SNAPSHOT")

  // resolvers += Resolver.sonatypeRepo("snapshots")
  ```

And of course, if you're serious about performance you should add the following line to your `build.sbt` file:
```scala
scalacOptions ++= Seq("-optimise", "-Yclosure-elim", "-Yinline")
```
(also consider `-Ybackend:GenBCode`)

## Cross-compiling with Sbt

Scalaxy/Streams is not available for Scala 2.10.x, so some extra legwork is needed to use it in a cross-compiling setup:

  ```scala
  scalaVersion := "2.11.6"
  
  crossScalaVersions := Seq("2.10.5")
  
  resolvers += Resolver.sonatypeRepo("snapshots"),
  
  libraryDependencies <<= (scalaVersion, libraryDependencies) { (scalaVersion, libraryDependencies) =>
    if (scalaVersion.matches("2\\.10\\..*")) {
      libraryDependencies
    } else {
      // libraryDependencies :+ compilerPlugin("com.nativelibs4java" %% "scalaxy-streams" % "0.3.4")
      libraryDependencies :+ compilerPlugin("com.nativelibs4java" %% "scalaxy-streams" % "0.4-SNAPSHOT")
    }
  }
  
  autoCompilerPlugins <<= scalaVersion(scalaVersion => !scalaVersion.matches("2\\.10\\..*"))
  
  scalacOptions <++= scalaVersion map {
    case sv if !scalaVersion.matches("2\\.10\\..*") => Seq("-Xplugin-require:scalaxy-streams")
    case _ => Seq[String]()
  }
  
  scalacOptions in Test ~= (_ filterNot (_ == "-Xplugin-require:scalaxy-streams"))
  
  scalacOptions in Test <++= scalaVersion map {
    case sv if !scalaVersion.matches("2\\.10\\..*") => Seq("-Xplugin-disable:scalaxy-streams")
    case _ => Seq[String]()
  }
  ```

## Usage with Maven

With Maven, you'll need this in your `pom.xml` file:

* To use the macro (manually decide which parts of your code are optimized):

  ```xml
  <dependencies>
    <dependency>
      <groupId>com.nativelibs4java</groupId>
      <artifactId>scalaxy-streams_2.11</artifactId>
      <version>0.3.4</version>
    </dependency>
  </dependencies>
  ```

  And to try the latest snapshot out:
  ```xml
  <dependencies>
    <dependency>
      <groupId>com.nativelibs4java</groupId>
      <artifactId>scalaxy-streams_2.11</artifactId>
      <version>0.4-SNAPSHOT</version>
    </dependency>
  </dependencies>

  <repositories>
    <repository>
      <id>sonatype-oss-public</id>
      <url>https://oss.sonatype.org/content/groups/public/</url>
    </repository>
  </repositories>
  ```

* To use the compiler plugin (optimizes all of your code):

  ```xml
  <properties>
    <scala.version>2.11.6</scala.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.scala-lang</groupId>
      <artifactId>scala-library</artifactId>
      <version>${scala.version}</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>net.alchim31.maven</groupId>
        <artifactId>scala-maven-plugin</artifactId>
        <version>3.1.6</version>
        <executions>
          <execution>
            <goals>
              <goal>compile</goal>
              <goal>testCompile</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <scalaVersion>${scala.version}</scalaVersion>
          <compilerPlugins>
            <compilerPlugin>
              <groupId>com.nativelibs4java</groupId>
              <artifactId>scalaxy-streams_${scala.version}</artifactId>
              <version>0.3.4</version>
            </compilerPlugin>
          </compilerPlugins>
        </configuration>
      </plugin>
    </plugins>
  </build>
  <repositories>
    <repository>
      <id>sonatype-oss-public</id>
      <name>Sonatype Snapshots</name>
      <url>https://oss.sonatype.org/content/groups/public/</url>
    </repository>
  </repositories>
  ```

## Usage with Eclipse

The Scalaxy/Stream compiler plugin is easy to setup in Eclipse with the Scala IDE plugin:
* Download the [stable scalaxy_streams_2.11 JAR on Maven Central](http://search.maven.org/remotecontent?filepath=com/nativelibs4java/scalaxy-streams_2.11/0.3.4/scalaxy-streams_2.11-0.3.4.jar), or the [snapshot JAR on Sonatype OSS](https://oss.sonatype.org/#nexus-search;quick~scalaxy_streams_2.11)
* Provide the full path to the jar in Project / Properties / Scala Compiler / Advanced / Xplugin

  ![advanced scala settings panel in eclipse](./Resources/wiki/scalaxy_settings_eclipse.png)

* That's it!

  ![scalaxy working in eclipse](./Resources/wiki/scalaxy_working_in_eclipse.png)

## A note on architecture

Scalaxy/Streams is a rewrite of [ScalaCL](https://code.google.com/p/scalacl/) using the awesome new (and experimental) reflection APIs from Scala 2.10, and the awesome [quasiquotes](http://docs.scala-lang.org/overviews/macros/quasiquotes.html) from Scala 2.11.

The architecture is very simple: Scalaxy/Streams deals with... streams. A stream is comprised of:

* A stream source (e.g. `ArrayStreamSource`, `InlineRangeStreamSource`...)
* A list of 1 or more stream operations (e.g. `MapOp`, `FilterOp`...)
* A stream sink (e.g. `ListBufferSink`, `OptionSink`...)
Each of these three kinds of stream components is able to emit the equivalent code of the rest of the stream, and generally has a corresponding extractor to recognize it in a `Tree` (e.g. `SomeArrayStreamSource`, `SomeOptionSink`, `SomeFlatMapOp`...).

One particular operation, `FlatMapOp`, may contain nested streams, which allows for the chaining of complex for comprehensions:
```scala
val n = 20;
// The following for comprehension:
for (i <- 0 to n;
     ii = i * i;
     j <- i to n;
     jj = j * j;
     if (ii - jj) % 2 == 0;
     k <- (i + j) to n)
  yield { (ii, jj, k) }

// Is recognized by Scalaxy/Stream as the following stream:
// Range.map.flatMap(Range.map.withFilter.flatMap(Range.map)) -> IndexedSeq
```

Special care is taken of tuples, by representing input and output values of stream components as *tuploids* (a tuploid is recursively defined as either a scalar or a tuple of tuploids).

Careful tracking of input, transformation and output of tuploids across stream components allows to optimize unneeded tuples away, while materializing or preserving needed ones (making `TransformationClosure` the most complex piece of code of the project).

A conservative whitelist-based side-effect analysis allows to detect "pure" functions (e.g. `(x: Int) => x + 1`), and "probably pure" ones (e.g. `(x: Any) => x.toString`). Different optimizations strategies then decide what is worth / safe to optimize (for instance, most `List` operations are highly optimized in the standard Scala library, so it only makes sense to optimize `List`-based streams if there's more than one operation in the call chain).

Finally, the cake pattern is used to assemble the source, ops, sink and stream extractors together with macro or compiler plugin universes.

# Optimization Strategies

Some collection streams should not be optimized, either because they're known to be already "as fast as possible", or because some of their operations have side-effects would behave differently once "optimized".

There are 4 different optimization strategies:
* `none`: don't optimize anything.
* `safe` (default): only perform rewrites with an expected runtime benefit; assumes some common methods are reasonably side-effect-free: `hashCode`, `toString`, `equals`, `+`, `++`...
* `safer`: like `safe` but does not trust `toString`, `equals`... methods (except when on truly immutable classes such as `Int`)
* `aggressive`: only perform rewrites with an expected runtime benefit, but don't pay attention to side-effects (warnings will be issued accordingly). Code optimized with the `aggressive` strategy might behave differently than normal code: for instance, streams typically become lazy (akin to chained Iterators), so the optimization might change the number and order of side-effects:

    ```scala
      import scalaxy.streams.optimize
      import scalaxy.streams.strategy.aggressive
      optimize {
        (1 to 2).map(i => { println("first map, " + i); i })
                .map(i => { println("second map, " + i); i })
                .take(1)
      }
      // Without optimizations, this would print:
      //   first map, 1
      //   first map, 2
      //   second map, 1
      //   second map, 2

      // With stream optimizations, this *semantically* amounts to the following:
      (1 to 2).toIterator
              .map(i => { println("first map, " + i); i })
              .map(i => { println("second map, " + i); i })
              .take(1)
              .toSeq
      // It will hence print:
      //   first map, 1
      //   second map, 1
    }
    ```

* `foolish`: rewrites everything it can, even if it doesn't make sense regarding side-effects or performance. Don't use this unless you have specific goals such as reducing your code's runtime dependency to the standard library ([ScalaCL](https://code.google.com/p/scalacl/) uses this strategy).

When using the `optimize` macro, strategies can be enabled locally by an import:

    ```scala
    import scalaxy.streams.optimize
    import scalaxy.streams.strategy.safer
    optimize {
      ...
    }
    ```

The global default strategy can also be set through the `SCALAXY_STREAMS_STRATEGY` environment variable, or the `scalaxy.streams.strategy` Java property:

    ```
    SCALAXY_STREAMS_STRATEGY=aggressive sbt clean run
    ```

    ```
    scalac -J-Dscalaxy.streams.strategy=aggressive ...
    ```

# Hacking / helping

Found a bug? Please [report it](https://github.com/ochafik/Scalaxy/issues/new) (your help will be much appreciated!).

If you want to build / test / hack on this project:

* Make sure to use [paulp's sbt script](https://github.com/paulp/sbt-extras) with `sbt` 0.13.0+
* Use the following commands to checkout the sources and build the tests continuously: 

        git clone git://github.com/ochafik/Scalaxy.git
        cd Scalaxy
        sbt "project scalaxy-streams" "; clean ; test-only *PerformanceTest"

* Want to see what's going on when you compile a project with Scalaxy/Streams?

        # Print internal trees before and after Scalaxy/Streams
        SCALAXY_STREAMS_OPTIMIZE=1 sbt 'set scalacOptions ++= Seq("-Xprint:typer", "-Xprint:scalaxy-streams")' clean compile

* Want to test performance?

        SCALAXY_TEST_PERF=1 sbt "project scalaxy-streams" "; clean ; ~test"

* Want to build scala itself with the plugin? (assumes you downloaded the plugin's JAR to the current directory and you've checked out the scala 2.11.x branch)

        # Make sure you've built locker:
        ant build locker.unlock
        # Make sure you're rebuilding quick:
        rm -fR build/quick
        # Build quick with the Scalaxy/Stream plugin:
        # (note: there are a couple of lingering problematic rewrites that must be skipped)
        SCALAXY_STREAMS_SKIP=LambdaLift.scala:searchIn \
          SCALAXY_STREAMS_VERY_VERBOSE=1 \
          ant "-Dscalac.args=-Xplugin-require:scalaxy-streams -Xplugin:${PWD}/scalaxy-streams_2.11.jar" build

        SCALAXY_STREAMS_VERY_VERBOSE=1 \
          ant "-Dscalac.args=\"-Xplugin:${PWD}/scalaxy-streams_2.11.jar\"" "-Dpartest.scalac_opts=\"-Xplugin:${PWD}/scalaxy-streams_2.11.jar\"" test

# Size optimizations

Incidentally, using Scalaxy will reduce the number of classes generated by scalac and will produce an overall smaller code. To witness the difference (68K vs. 172K as of June 12th 2014):

        git clone git://github.com/ochafik/Scalaxy.git
        cd Scalaxy/Example
        SCALAXY_STREAMS_OPTIMIZE=1 sbt clean compile && du -h target/scala-2.11/classes/
        SCALAXY_STREAMS_OPTIMIZE=0 sbt clean compile && du -h target/scala-2.11/classes/

# Changelog

* 0.4-SNAPSHOT
  * ...
  * Added an `experimental` mode (`SCALAXY_STREAMS_EXPERIMENTAL=1` or `-Dscalaxy.streams.experimental=true`) for optimizations known to cause clean compilation crashes in some circumstances
  * Added `reduceLeft` op
  * Added `Iterable` sources (no sinks yet)
  * Added `js.Array` & `js.ArrayOps` sources (for [Scala.js](www.scala-js.org))
  * Added `flatten` op (in `experimental` mode),
  * Added `take`, `drop` ops
  * Added `mkString` op
  * Added `Option.orElse` (w/ nested streams)
  * Prevent `takeWhile` & `dropWhile` on `Option`
  * Mitigate known [issue #20](https://github.com/ochafik/Scalaxy/issues/20): just don't optimize try/catch sub-trees yet.
* 0.3.4 (2014-11-04):
  * Fixed `RuntimeException: scala.Array.length : Object, Object, Any`
  * Fixed `Option(null).getOrElse(...)`
  * Support `Option.orNull`, `Option.isDefined`
  * Support `isEmpty` / `nonEmpty` for all collections (not just `Option`s)
* Version 0.3.3 (2014-10-26):
  * Fix coercion as in `for ((a, b) <- List(null, (1, 2)) print(a + b)`
  * Fix side-effect strategies wrt/ size-altering ops (`filter` after a `map` with side-effects is not safe to optimize)
  * Fix warning `a pure expression does nothing in statement position...` (issue #16)
  * Fix `found : Null(null)` exception with Option sources
* Version 0.3.2 (2014-10-22):
  * More bugfixes for `flatMap`
* Version 0.3.1 (2014-10-22):
  * Bugfixes for tuples (`i => (i, i)`), for `flatMap`...
* Version 0.3.0 (2014-10-21):
  * Safe rewrites by default, configurable optimization strategies, more sources / ops (Option, flatMap...)
* Version 0.2.1 (2014-07-01):
  * Bugfixes
* Version 0.2.0 (2014-06-12):
  * Unsafe rewrites with tuple rewiring.

# TODO

* Track lingering exceptions of scala-library with the plugin
  * Only one to go: `Typers.scala:computeParamAliases`
* Support `toIterator`
* Treat *iterated* ops specially with regards to side-effects (anything after `toIterator`, and any `withFilter` op).
  * For instance `List.withFilter(side-effects).foreach(side-effects)` should be considered safe to optimize (unlike `List.filter(side-effects).foreach(side-effects)`)
* Support the following rewrites:
  * `reduce`, `foldLeft`, `scanLeft`...
  * `indexWhere`,
  * ...?
* Size hints on filtered arrays?
* Experiment with more optimization metrics: number of intermediate collections seems better than lambdaCount?
* Improve performance tests with compilation time, binary size and peak memory usage measurements:

  ```scala

  import java.lang.management.{ ManagementFactory, MemoryMXBean, MemoryPoolMXBean }
  import collection.JavaConversions._

  for (pool <- ManagementFactory.getMemoryPoolMXBeans) {
    println(String.format("%s: %,d", pool.getName, pool.getPeakUsage.getUsed.asInstanceOf[AnyRef]))
  }
  ```

# Why is this not part of Scala?

Good question! When ScalaCL was announced in 2009, it generated lots of interest in the community, but at that time it was just a dirty hack.

Crafting an optimization engine that works in all cases / doesn't introduce bugs is a very hard and time-consuming problem, and I've had very little time for Scala in the past years. And most of that time was burnt rewriting ScalaCL / Scalaxy a couple of times because of API changes (painfully bleeding-edge RCs, new reflect API, quasiquotes...) and because I got less... unexperienced.

As for the Scala Team, I did get some enthusiastic reaction / life-saving advice from Paul Philips and Eugene Burmako, but my understanding is that the official approach to optimizations is to not do any library-specific hacks (see [this thread](http://www.scala-lang.org/old/node/7901.html)). Making inlining work well in general is already big enough a challenge and they have limited compiler developer resources. And Scalaxy/Streams works well outside the compiler for the adventurous users (and optimizes Scala itself just fine: [![CircleCI branch](https://img.shields.io/circleci/project/BrightFlair/PHP.Gt/master.svg?label=scala-opt)](https://circleci.com/gh/nativelibs4java/scalaxy-streams)), so why make the compiler more complex?

*Coughs* if one of the Scala forks ([TypeLevel](http://typelevel.org/blog/2014/09/02/typelevel-scala.html), [policy](https://github.com/paulp/policy)) want to bundle Scalaxy/Streams in their distro, well, be my BSD license guest.

And if anyone wants to tackle [SI-1338](https://issues.scala-lang.org/browse/SI-1338), I'm happy to help (a bit :-)).

# Self-optimization

Scalaxy/Streams can optimize itself, although that is still being experimented with:
```
./Resources/scripts/self_optimize.sh
```

To use the (experimental!) self-optimized compiler plugin / macros:
  ```scala
  scalaVersion := "2.11.6"

  autoCompilerPlugins := true

  addCompilerPlugin("com.nativelibs4java" %% "scalaxy-streams-experimental-self-optimized" % "0.4-SNAPSHOT")

  scalacOptions += "-Xplugin-require:scalaxy-streams"

  resolvers += Resolver.sonatypeRepo("snapshots")
  ```

Your feedback is precious: did you run into issues with the self-optimized plugin? Do you find it any faster?

(TODO(ochafik): check that the self-optimized version compiles scala itself just as fine as the original)
