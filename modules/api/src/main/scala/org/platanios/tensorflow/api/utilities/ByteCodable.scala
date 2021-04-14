/* Copyright 2017-19, Emmanouil Antonios Platanios. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.platanios.tensorflow.api.utilities

import java.nio.{ByteBuffer, ByteOrder}

import org.platanios.tensorflow.api.core.Shape
import org.platanios.tensorflow.api.core.types.{DataType, TF}

// TODO: Support more data structures (e.g., using shapeless), and generalize `Seq` to collections.

trait ByteCodable[T] {
  type Scalar

  def byteCount(value: T): Int
  def convertToByteArray(value: T, shape: Option[Shape] = None): (Array[Byte], Shape)
}

object ByteCodable {
  def apply[T](implicit ev: ByteCodable[T]): Aux[T, ev.Scalar] = {
    ev.asInstanceOf[Aux[T, ev.Scalar]]
  }

  type Aux[T, S] = ByteCodable[T] {
    type Scalar = S
  }

  implicit def dataTypeByteCodable[T: TF]: Aux[T, T] = new ByteCodable[T] {
    override type Scalar = T

    override def byteCount(value: T): Int = TF[T].dataType.nativeByteSize.get

    override def convertToByteArray(value: T, shape: Option[Shape] = None): (Array[Byte], Shape) = {
      val buffer = ByteBuffer.allocate(byteCount(value)).order(ByteOrder.LITTLE_ENDIAN)
      DataType.putElementInBuffer(buffer, 0, value)
      (buffer.array(), Shape())
    }
  }

  implicit def arrayByteCodable[T](implicit ev: ByteCodable[T]): Aux[Array[T], ev.Scalar] = new ByteCodable[Array[T]] {
    override type Scalar = ev.Scalar

    override def byteCount(value: Array[T]): Int = value.map(ByteCodable[T].byteCount).sum

    override def convertToByteArray(value: Array[T], shape: Option[Shape] = None): (Array[Byte], Shape) = {
      require(
        value.nonEmpty || shape.isDefined,
        "A shape must be provided when converting empty arrays to byte arrays.",
      )
      val results = value.map(ByteCodable[T].convertToByteArray(_, shape.map(_.drop(1))))
      require(
        results.isEmpty || results.forall(_._2.asArray.sameElements(results.head._2.asArray)),
        "All nested arrays must have the same size.",
      )
      require(
        shape.isEmpty || results.isEmpty || shape.forall(_.isCompatibleWith(Shape(value.length) ++ results.head._2)),
        "The provided shape is not compatible with the provided values."
      )
      (results.flatMap(_._1), shape.getOrElse(Shape(value.length) ++ results.head._2))
    }
  }

  implicit def seqByteCodable[T](implicit ev: ByteCodable[T]): Aux[Seq[T], ev.Scalar] = new ByteCodable[Seq[T]] {
    override type Scalar = ev.Scalar

    override def byteCount(value: Seq[T]): Int = value.map(ByteCodable[T].byteCount).sum

    override def convertToByteArray(value: Seq[T], shape: Option[Shape] = None): (Array[Byte], Shape) = {
      require(
        value.nonEmpty || shape.isDefined,
        "A shape must be provided when converting empty sequences to byte arrays.",
      )
      val results = value.map(ByteCodable[T].convertToByteArray(_, shape.map(_.drop(1))))
      require(
        results.isEmpty || results.forall(_._2.asArray.sameElements(results.head._2.asArray)),
        "All nested arrays must have the same size.",
      )
      require(
        shape.isEmpty || results.isEmpty || shape.forall(_.isCompatibleWith(Shape(value.length) ++ results.head._2)),
        "The provided shape is not compatible with the provided values."
      )
      (results.flatMap(_._1).toArray, shape.getOrElse(Shape(value.length) ++ results.head._2))
    }
  }
}
