package org.scaladebugger.api.profiles.pure.info

import com.sun.jdi._
import org.scaladebugger.api.lowlevel.{InvokeNonVirtualArgument, InvokeSingleThreadedArgument, JDIArgument}
import org.scaladebugger.api.profiles.traits.info._
import org.scaladebugger.api.virtualmachines.ScalaVirtualMachine
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FunSpec, Matchers, ParallelTestExecution}

class PureObjectInfoProfileSpec extends FunSpec with Matchers
  with ParallelTestExecution with MockFactory
{
  private val mockNewFieldProfile = mockFunction[Field, VariableInfoProfile]
  private val mockNewMethodProfile = mockFunction[Method, MethodInfoProfile]
  private val mockNewValueProfile = mockFunction[Value, ValueInfoProfile]
  private val mockNewTypeCheckerProfile = mockFunction[TypeCheckerProfile]
  private val mockNewReferenceTypeProfile = mockFunction[ReferenceType, ReferenceTypeInfoProfile]
  private val mockScalaVirtualMachine = mock[ScalaVirtualMachine]
  private val mockVirtualMachine = mock[VirtualMachine]
  private val mockReferenceType = mock[ReferenceType]
  private val mockThreadReference = mock[ThreadReference]
  private val mockObjectReference = mock[ObjectReference]
  private val pureObjectInfoProfile = new PureObjectInfoProfile(
    mockScalaVirtualMachine,
    mockObjectReference
  )(
    threadReference = mockThreadReference,
    virtualMachine = mockVirtualMachine,
    referenceType = mockReferenceType
  ) {
    override protected def newFieldProfile(field: Field): VariableInfoProfile =
      mockNewFieldProfile(field)
    override protected def newMethodProfile(method: Method): MethodInfoProfile =
      mockNewMethodProfile(method)
    override protected def newValueProfile(value: Value): ValueInfoProfile =
      mockNewValueProfile(value)
    override protected def newTypeCheckerProfile(): TypeCheckerProfile =
      mockNewTypeCheckerProfile()
    override protected def newReferenceTypeProfile(
      referenceType: ReferenceType
    ): ReferenceTypeInfoProfile = mockNewReferenceTypeProfile(referenceType)
  }

  describe("PureObjectInfoProfile") {
    describe("#toJdiInstance") {
      it("should return the JDI instance this profile instance represents") {
        val expected = mockObjectReference

        val actual = pureObjectInfoProfile.toJdiInstance

        actual should be (expected)
      }
    }

    describe("#uniqueId") {
      it("should return the unique id of the object") {
        val expected = 12345L

        (mockObjectReference.uniqueID _).expects().returning(expected).once()

        val actual = pureObjectInfoProfile.uniqueId

        actual should be (expected)
      }
    }

    describe("#getReferenceType") {
      it("should return a profile wrapping the object's reference type") {
        val expected = mock[ReferenceTypeInfoProfile]
        val mockReferenceType = mock[ReferenceType]

        (mockObjectReference.referenceType _).expects()
          .returning(mockReferenceType).once()

        mockNewReferenceTypeProfile.expects(mockReferenceType)
          .returning(expected).once()

        val actual = pureObjectInfoProfile.getReferenceType

        actual should be (expected)
      }
    }

    describe("#invoke(methodName, parameter types, arguments, JDI arguments)") {
      it("should throw an AssertionError if parameter types and arguments are not same length") {
        intercept[AssertionError] {
          pureObjectInfoProfile.invoke("name", Nil, Seq(3))
        }
      }

      it("should use unsafeMethod to search for method with name and parameter types") {
        val expected = mock[ValueInfoProfile]

        val name = "methodName"
        val parameterTypeNames = Seq("some.type")
        val arguments = Seq(3)
        val jdiArguments = Nil

        val mockUnsafeInvoke = mockFunction[
          MethodInfoProfile,
          Seq[Any],
          Seq[JDIArgument],
          ValueInfoProfile
        ]
        val mockUnsafeMethod = mockFunction[String, Seq[String], MethodInfoProfile]

        val pureObjectInfoProfile = new PureObjectInfoProfile(
          mockScalaVirtualMachine,
          mockObjectReference
        )(
          threadReference = mockThreadReference,
          virtualMachine = mockVirtualMachine,
          referenceType = mockReferenceType
        ) {
          override def invoke(
            methodInfoProfile: MethodInfoProfile,
            arguments: Seq[Any],
            jdiArguments: JDIArgument*
          ): ValueInfoProfile = mockUnsafeInvoke(
            methodInfoProfile,
            arguments,
            jdiArguments
          )
          override def getMethod(
            name: String,
            parameterTypeNames: String*
          ): MethodInfoProfile = mockUnsafeMethod(name, parameterTypeNames)
        }

        val mockMethodInfoProfile = mock[MethodInfoProfile]
        mockUnsafeMethod.expects(name, parameterTypeNames)
          .returning(mockMethodInfoProfile).once()

        mockUnsafeInvoke.expects(mockMethodInfoProfile, arguments, jdiArguments)
          .returning(expected).once()

        val actual = pureObjectInfoProfile.invoke(
          name,
          parameterTypeNames,
          arguments,
          jdiArguments: _*
        )

        actual should be (expected)
      }
    }

    describe("#invoke(method profile, arguments, JDI arguments)") {
      it("should invoke using the current stack frame's thread and profile method, returning wrapper profile of value") {
        val expected = mock[ValueInfoProfile]

        val mockMethod = mock[Method]
        val pureMethodInfoProfile = new PureMethodInfoProfile(
          mockScalaVirtualMachine,
          mockMethod
        )

        // Object method is invoked
        val mockValue = mock[Value]
        import scala.collection.JavaConverters._
        (mockObjectReference.invokeMethod _).expects(
          mockThreadReference,
          mockMethod,
          Seq[Value]().asJava,
          0
        ).returning(mockValue).once()

        // Profile is created for return value
        mockNewValueProfile.expects(mockValue).returning(expected).once()

        val actual = pureObjectInfoProfile.invoke(
          pureMethodInfoProfile,
          Nil
        )

        actual should be (expected)
      }

      it("should invoke using the provided arguments") {
        val arguments = Seq(1)

        val mockMethod = mock[Method]
        val pureMethodInfoProfile = new PureMethodInfoProfile(
          mockScalaVirtualMachine,
          mockMethod
        )

        // Arguments are mirrored remotely
        val mockValues = Seq(mock[IntegerValue])
        arguments.zip(mockValues).foreach { case (ar, ma) =>
          (mockVirtualMachine.mirrorOf(_: Int)).expects(ar)
            .returning(ma).once()
        }

        // Object method is invoked
        val mockValue = mock[Value]
        import scala.collection.JavaConverters._
        (mockObjectReference.invokeMethod _).expects(
          *,
          *,
          mockValues.asJava,
          *
        ).returning(mockValue).once()

        // Profile is created for return value
        mockNewValueProfile.expects(*).once()

        pureObjectInfoProfile.invoke(
          pureMethodInfoProfile,
          arguments
        )
      }

      it("should provide relevant JDI options as an OR'd value") {
        val jdiArguments = Seq(
          InvokeNonVirtualArgument,
          InvokeSingleThreadedArgument
        )

        val mockMethod = mock[Method]
        val pureMethodInfoProfile = new PureMethodInfoProfile(
          mockScalaVirtualMachine,
          mockMethod
        )

        // Object method is invoked
        // NOTE: Both arguments OR'd together is 3 (1 | 2)
        (mockObjectReference.invokeMethod _).expects(*, *, *, 3)
          .returning(mock[Value]).once()

        // Profile is created for return value
        mockNewValueProfile.expects(*).returning(null).once()

        pureObjectInfoProfile.invoke(
          pureMethodInfoProfile,
          Nil,
          jdiArguments: _*
        )
      }
    }

    describe("#getMethods") {
      it("should return a collection of profiles wrapping the object's visible methods") {
        val expected = Seq(mock[MethodInfoProfile])

        // Lookup the visible methods
        import scala.collection.JavaConverters._
        val mockMethods = Seq(mock[Method])
        (mockReferenceType.visibleMethods _).expects()
          .returning(mockMethods.asJava).once()

        // Create the new profiles for the methods
        mockMethods.zip(expected).foreach { case (m, e) =>
          mockNewMethodProfile.expects(m).returning(e).once()
        }

        val actual = pureObjectInfoProfile.getMethods

        actual should be (expected)
      }
    }

    describe("#getMethod") {
      it("should throw a NoSuchElement exception if no method with matching name is found") {
        val name = "someName"
        val paramTypes = Seq("some.type")

        // Lookup the method and return empty list indicating no method found
        import scala.collection.JavaConverters._
        (mockReferenceType.methodsByName(_: String)).expects(name)
          .returning(Seq[Method]().asJava).once()

        intercept[NoSuchElementException] {
          pureObjectInfoProfile.getMethod(name, paramTypes: _*)
        }
      }

      it("should throw a NoSuchElement exception if no method with matching parameters is found") {
        val name = "someName"
        val paramTypes = Seq("some.type")

        // Lookup the method and return method indicating matching name found
        val mockMethod = mock[Method]
        import scala.collection.JavaConverters._
        (mockReferenceType.methodsByName(_: String)).expects(name)
          .returning(Seq(mockMethod).asJava).once()

        (mockMethod.argumentTypeNames _).expects()
          .returning(paramTypes.map(_ + "other").asJava).once()

        // Arguments do not match, so return false
        val mockTypeCheckerProfile = mock[TypeCheckerProfile]
        mockNewTypeCheckerProfile.expects()
          .returning(mockTypeCheckerProfile).once()
        (mockTypeCheckerProfile.equalTypeNames _).expects(*, *)
          .returning(false).once()

        intercept[NoSuchElementException] {
          pureObjectInfoProfile.getMethod(name, paramTypes: _*)
        }
      }

      it("should return a profile wrapping the associated method if found") {
        val expected = mock[MethodInfoProfile]

        val name = "someName"
        val paramTypes = Seq("some.type")

        // Lookup the method and return method indicating matching name found
        val mockMethod = mock[Method]
        import scala.collection.JavaConverters._
        (mockReferenceType.methodsByName(_: String)).expects(name)
          .returning(Seq(mockMethod).asJava).once()

        (mockMethod.argumentTypeNames _).expects()
          .returning(paramTypes.asJava).once()

        // Arguments do match, so return true
        val mockTypeCheckerProfile = mock[TypeCheckerProfile]
        mockNewTypeCheckerProfile.expects()
          .returning(mockTypeCheckerProfile).once()
        (mockTypeCheckerProfile.equalTypeNames _).expects(*, *)
          .returning(true).once()

        // New method profile created
        mockNewMethodProfile.expects(mockMethod).returning(expected).once()

        val actual = pureObjectInfoProfile.getMethod(name, paramTypes: _*)

        actual should be (expected)
      }
    }

    describe("#getFields") {
      it("should return a collection of profiles wrapping the object's visible fields") {
        val expected = Seq(mock[VariableInfoProfile])

        // Lookup the visible fields
        import scala.collection.JavaConverters._
        val mockFields = Seq(mock[Field])
        (mockReferenceType.visibleFields _).expects()
          .returning(mockFields.asJava).once()

        // Create the new profiles for the fields
        mockFields.zip(expected).foreach { case (f, e) =>
          mockNewFieldProfile.expects(f).returning(e).once()
        }

        val actual = pureObjectInfoProfile.getFields

        actual should be (expected)
      }
    }

    describe("#getField") {
      it("should throw a NoSuchElement exception if no field with matching name is found") {
        val name = "someName"

        // Lookup the field and return null indicating no field found
        (mockReferenceType.fieldByName _).expects(name)
          .returning(null).once()

        intercept[NoSuchElementException] {
          pureObjectInfoProfile.getField(name)
        }
      }

      it("should return a profile wrapping the associated field if found") {
        val expected = mock[VariableInfoProfile]
        val name = "someName"

        // Lookup the field
        val mockField = mock[Field]
        (mockReferenceType.fieldByName _).expects(name)
          .returning(mockField).once()

        // Create the new profile
        mockNewFieldProfile.expects(mockField).returning(expected).once()

        val actual = pureObjectInfoProfile.getField(name)

        actual should be (expected)
      }
    }
  }
}