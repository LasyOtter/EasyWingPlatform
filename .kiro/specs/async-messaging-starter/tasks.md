# Implementation Plan: async-messaging-starter

## Overview

This implementation plan breaks down the async-messaging-starter feature into discrete coding tasks. The feature provides a unified messaging abstraction layer for Spring Boot microservices, supporting both Kafka and RabbitMQ. Implementation follows a bottom-up approach: core interfaces → data models → converters → interceptors → adapters → template implementation → Spring Boot auto-configuration.

## Tasks

- [x] 1. Set up project structure and core interfaces
  - Create Maven/Gradle project structure with Spring Boot dependencies
  - Define core package structure (core, adapter, converter, interceptor, config)
  - Create core exception classes (MessagingException, ConversionException)
  - Create core data models (Message, MessageHeaders, SendResult, MessageContext)
  - _Requirements: 9.1, 9.2, 9.3, 9.4, 10.1, 10.2, 10.3, 10.4_

- [ ] 2. Implement core messaging interfaces
  - [x] 2.1 Create MessageTemplate interface
    - Define send, sendAsync, and sendInTransaction methods
    - Add proper JavaDoc documentation
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3_
  
  - [x] 2.2 Create MessageListener interface
    - Define onMessage method with generic type support
    - Add @FunctionalInterface annotation
    - _Requirements: 4.1, 4.2_
  
  - [x] 2.3 Create MessageConverter interface
    - Define toBytes, fromBytes, and supports methods
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  
  - [x] 2.4 Create MessageInterceptor interface
    - Define preSend, postSend, preReceive, postReceive methods
    - Add getOrder method with default implementation
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_
  
  - [x] 2.5 Create MessagingAdapter interface
    - Define doSend, doSendAsync, registerListener, getAdapterType methods
    - _Requirements: 7.1, 8.1_

- [ ] 3. Implement message data models
  - [x] 3.1 Create Message class
    - Implement generic payload storage
    - Implement MessageHeaders integration
    - Add builder pattern for easy construction
    - _Requirements: 9.1, 9.3, 9.4_
  
  - [x] 3.2 Create MessageHeaders class
    - Implement header storage with type-safe get methods
    - Auto-generate unique message ID
    - Support common headers (messageKey, partition, timestamp, etc.)
    - _Requirements: 9.2, 9.3_
  
  - [x] 3.3 Create SendResult class
    - Store send status, destination, message ID, and metadata
    - Provide factory methods (success, failure)
    - _Requirements: 1.2_
  
  - [x] 3.4 Create MessageContext class
    - Store message metadata and processing information
    - Include destination, headers, and adapter type
    - _Requirements: 4.2_

- [ ] 4. Implement message converter
  - [x] 4.1 Create JsonMessageConverter implementation
    - Use Jackson ObjectMapper for JSON serialization
    - Implement toBytes method with error handling
    - Implement fromBytes method with type conversion
    - Implement supports method to check type compatibility
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 10.2_
  
  - [~] 4.2 Write unit tests for JsonMessageConverter
    - Test serialization of various object types
    - Test deserialization with correct types
    - Test error handling for invalid JSON
    - Test supports method for different types
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [ ] 5. Implement AbstractMessageTemplate
  - [x] 5.1 Create AbstractMessageTemplate base class
    - Implement constructor with adapter, converter, interceptors, properties
    - Implement interceptor sorting by order
    - Implement synchronous send method with interceptor chain
    - Implement wrapMessage abstract method
    - Implement applyPreSendInterceptors and applyPostSendInterceptors
    - _Requirements: 1.1, 1.2, 1.3, 6.1, 6.2, 6.5, 9.1_
  
  - [x] 5.2 Implement asynchronous send methods in AbstractMessageTemplate
    - Implement sendAsync returning CompletableFuture
    - Implement sendAsync with SendCallback
    - Apply interceptor chain for async operations
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 6.1, 6.2_
  
  - [x] 5.3 Implement transaction message support
    - Implement sendInTransaction method
    - Execute TransactionExecutor before sending
    - Handle rollback on executor failure
    - Handle exception on send failure after successful execution
    - _Requirements: 3.1, 3.2, 3.3, 10.1_

- [x] 6. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. Implement Kafka adapter
  - [x] 7.1 Create KafkaMessagingAdapter class
    - Implement constructor with KafkaTemplate and MessageConverter
    - Initialize listener registry (ConcurrentHashMap)
    - Implement getAdapterType returning "kafka"
    - _Requirements: 7.1, 7.5_
  
  - [x] 7.2 Implement Kafka synchronous send
    - Implement doSend method using KafkaTemplate
    - Create ProducerRecord with topic, partition, key, and payload
    - Convert message payload to bytes using MessageConverter
    - Add message headers to Kafka record headers
    - Convert Kafka SendResult to framework SendResult
    - _Requirements: 7.2, 7.3, 7.4, 10.3_
  
  - [x] 7.3 Implement Kafka asynchronous send
    - Implement doSendAsync method with callback support
    - Use KafkaTemplate's async send with ListenableFuture
    - Convert success/failure to SendCallback invocations
    - _Requirements: 7.2, 7.3, 7.4_
  
  - [~] 7.4 Implement Kafka listener registration
    - Implement registerListener method
    - Store listener in registry by destination
    - Create Kafka consumer configuration for listener
    - _Requirements: 4.1_
  
  - [~] 7.5 Write unit tests for KafkaMessagingAdapter
    - Test synchronous send with mocked KafkaTemplate
    - Test asynchronous send with callback
    - Test partition key handling
    - Test header propagation
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [ ] 8. Implement RabbitMQ adapter
  - [x] 8.1 Create RabbitMQMessagingAdapter class
    - Implement constructor with RabbitTemplate and MessageConverter
    - Initialize listener registry (ConcurrentHashMap)
    - Implement getAdapterType returning "rabbitmq"
    - _Requirements: 8.1, 8.5_
  
  - [x] 8.2 Implement RabbitMQ destination parsing
    - Create parseDestination method to split "exchange/routingKey"
    - Handle format without "/" (use default exchange)
    - _Requirements: 8.2, 8.3_
  
  - [x] 8.3 Implement RabbitMQ synchronous send
    - Implement doSend method using RabbitTemplate
    - Parse destination to extract exchange and routingKey
    - Convert message payload to bytes using MessageConverter
    - Create AMQP Message with headers
    - Send using RabbitTemplate.send
    - _Requirements: 8.2, 8.3, 10.3_
  
  - [x] 8.4 Implement RabbitMQ asynchronous send
    - Implement doSendAsync with CorrelationData
    - Use CorrelationData.getFuture for callback handling
    - Convert publisher confirms to SendCallback invocations
    - _Requirements: 8.4_
  
  - [x] 8.5 Implement RabbitMQ listener registration
    - Implement registerListener method
    - Store listener in registry by destination
    - Create RabbitMQ listener container configuration
    - _Requirements: 4.1_
  
  - [~] 8.6 Write unit tests for RabbitMQMessagingAdapter
    - Test synchronous send with mocked RabbitTemplate
    - Test destination parsing (with and without "/")
    - Test asynchronous send with CorrelationData
    - Test header propagation
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [ ] 9. Implement message listener adapter
  - [x] 9.1 Create MessageListenerAdapter class
    - Wrap user-provided MessageListener
    - Apply preReceive interceptors before listener invocation
    - Apply postReceive interceptors after listener invocation
    - Handle exceptions and trigger retry/DLQ logic
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 6.3, 6.4_
  
  - [~] 9.2 Write unit tests for MessageListenerAdapter
    - Test successful message processing
    - Test interceptor chain execution
    - Test exception handling and retry
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [ ] 10. Implement built-in interceptors
  - [x] 10.1 Create TracingInterceptor
    - Add trace ID to message headers in preSend
    - Extract trace ID from headers in preReceive
    - Set trace context for distributed tracing
    - _Requirements: 6.1, 6.3_
  
  - [x] 10.2 Create MetricsInterceptor
    - Record send metrics in postSend (success/failure counts, latency)
    - Record receive metrics in postReceive (processing time, success/failure)
    - Integrate with Micrometer or similar metrics library
    - _Requirements: 6.2, 6.4_
  
  - [~] 10.3 Write unit tests for interceptors
    - Test TracingInterceptor adds and extracts trace IDs
    - Test MetricsInterceptor records metrics correctly
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

- [x] 11. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 12. Implement Spring Boot auto-configuration
  - [~] 12.1 Create MessagingProperties configuration class
    - Define properties for adapter type (kafka/rabbitmq)
    - Define properties for retry, timeout, and DLQ configuration
    - Use @ConfigurationProperties annotation
    - _Requirements: 4.4_
  
  - [~] 12.2 Create KafkaAutoConfiguration
    - Conditionally create KafkaMessagingAdapter bean when Kafka is on classpath
    - Configure KafkaTemplate with appropriate serializers
    - Register MessageConverter bean
    - _Requirements: 7.1, 7.2_
  
  - [~] 12.3 Create RabbitMQAutoConfiguration
    - Conditionally create RabbitMQMessagingAdapter bean when RabbitMQ is on classpath
    - Configure RabbitTemplate with appropriate converters
    - Register MessageConverter bean
    - _Requirements: 8.1, 8.2_
  
  - [~] 12.4 Create MessagingAutoConfiguration
    - Register MessageTemplate bean using selected adapter
    - Register built-in interceptors (TracingInterceptor, MetricsInterceptor)
    - Configure interceptor ordering
    - Load MessagingProperties
    - _Requirements: 6.5_
  
  - [~] 12.5 Create spring.factories file
    - Register all auto-configuration classes
    - Ensure proper ordering of configurations

- [ ] 13. Create annotation-based listener support
  - [~] 13.1 Create @MessageHandler annotation
    - Define annotation with destination attribute
    - Support method-level annotation
    - _Requirements: 4.1_
  
  - [~] 13.2 Create MessageHandlerBeanPostProcessor
    - Scan beans for @MessageHandler annotated methods
    - Create MessageListener wrapper for each annotated method
    - Register listeners with MessagingAdapter
    - _Requirements: 4.1, 4.2_
  
  - [~] 13.3 Write integration tests for annotation-based listeners
    - Test listener registration and invocation
    - Test message deserialization to method parameter type
    - _Requirements: 4.1, 4.2_

- [ ] 14. Create example usage and documentation
  - [~] 14.1 Create example Spring Boot application
    - Demonstrate synchronous and asynchronous message sending
    - Demonstrate message listener registration
    - Show both Kafka and RabbitMQ configurations
    - _Requirements: 1.1, 2.1, 4.1_
  
  - [~] 14.2 Create README.md with usage guide
    - Document dependency configuration
    - Document application.properties configuration
    - Provide code examples for common use cases
    - Document custom interceptor creation

- [~] 15. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Implementation uses Java and Spring Boot framework
- Checkpoints ensure incremental validation
- Focus on creating a minimal but complete implementation
- All core interfaces and adapters must be implemented before auto-configuration
- Testing tasks validate correctness but are optional for initial implementation
