# RabbitMQ over JMS

Some experiments to see if it is possible to access RabbitMQ using the JMS API.

## Start RabbitMQ
The `rabbitmq/` folder contains a ready-to-run Dockerfile for experimenting on your own machine.
Start RabbitMQ with `docker run --name rabbit-with-jms -p 5672:5672 -p 15672:15672 -e RABBITMQ_DEFAULT_USER=user -e RABBITMQ_DEFAULT_PASS=password <whatever-password>`

## Start the app
Deploy the app to Tomcat under `/rabbitmq` and use cURL to call it: `curl -v http://localhost:8080/rabbitmq/`.

## References

* [RabbitMQ documentation -  RabbitMQ JMS Client ](https://www.rabbitmq.com/jms-client.html)
