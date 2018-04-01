# Library for bean conversion
Uses bytecode generation for generating the convert class.
Type conversion is done by using InvokeDynamic to retrieve the specific converter.


# Development status
## Done
* Generate converter class
  * creating new instance of target type
  * mapping simple types
* Multiple strategies to create beans
  * constructor
  * interface -> implementation
  * supplier
  * Static factory methods: getInstance(), newInstance()
* Search for type conversion
  * general conversions
  * using instance method on the source type
    * Groovy like: public Type asType()
    * Spring framework like: public Type toType()
  * using manually added converters
  * throw exception if no converter is found

## To do
* Generics support
* Beans with constructor arguments
* Generate mapping
  * using code
  * using configuration file


# Supported type conversions
* string -> string
* primitive -> wrapper
* wrapper -> primitive
* string -> number
* number -> string
* string -> enum
* number -> enum
