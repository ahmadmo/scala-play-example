# bama-api-demo
An API demo for the [Bama](https://bama.ir) website—A place for buy and sell new & used cars online—with almost the same functionality.

Completely written in [Scala](http://www.scala-lang.org/) programming language, with the help of
 * Play framework [v2.5.14](https://playframework.com/)
 * Slick FRM [v3.2.0](http://slick.lightbend.com/)

What you will find in this demo:
 * How to add more abstraction to Slick ([here](https://github.com/ahmadmo/bama-api-demo/blob/master/app/ir/bama/repositories/BaseRepo.scala))
 * Auto table generation on startup using Slick ([here](https://github.com/ahmadmo/bama-api-demo/blob/master/app/ir/bama/controllers/Application.scala))
 * Handling complex forms in Play! ([here](https://github.com/ahmadmo/bama-api-demo/blob/master/app/ir/bama/controllers/SellAdController.scala) or [here](https://github.com/ahmadmo/bama-api-demo/blob/master/app/ir/bama/controllers/SellerController.scala))
 * Token-based Authentication using [JWT](https://jwt.io/) ([here](https://github.com/ahmadmo/bama-api-demo/blob/master/app/ir/bama/controllers/AuthController.scala))
 * Advanced scheduling with akka ([here](https://github.com/ahmadmo/bama-api-demo/blob/master/conf/application.conf#L36) and [here](https://github.com/ahmadmo/bama-api-demo/blob/master/app/ir/bama/controllers/AuthController.scala#L56))
 * Managing concurrent db inserts/updates using akka actors ([here](https://github.com/ahmadmo/bama-api-demo/blob/master/app/ir/bama/services/SellAdService.scala#L66))
 * Working with shapeless tuples ([here](https://github.com/ahmadmo/bama-api-demo/blob/master/app/ir/bama/repositories/SellAdRepo.scala#L253)) and lenses ([here](https://github.com/ahmadmo/bama-api-demo/blob/master/app/ir/bama/models/Seller.scala))
 * etc.

### DB configuration
Currently using H2 db (see [here](https://github.com/ahmadmo/bama-api-demo/blob/master/conf/application.conf#L367)), but you can easily replace it with your favorite db. ([More information](https://www.playframework.com/documentation/2.5.x/PlaySlick))

### TODO
 * Source code documentation
 * Add more functionalities & features
 * Play with Slick thread-pool & connection-pool configurations
 * Do some load & performance tests using [gatling](http://gatling.io/)
 * Provide comparison between Slick and Hibernate ORM
 * what else?

### API

```play
# Location
GET                 /province/list
GET                 /city/list/:provinceId

# Car Brand & Model
POST                /car/brand/add
GET                 /car/brand/list
GET                 /car/brand/search/:name
POST                /car/model/:brandId/add
GET                 /car/model/list/:brandId
GET                 /car/model/search/:brandId/:name

# Auth
POST                /auth/login
GET                 /auth/me
GET                 /auth/login/list
POST                /auth/token
POST                /auth/logout

# File
GET                 /file/:name

# Seller
POST                /seller/register
PUT                 /seller/photo/upload
DELETE              /seller/photo/delete
GET                 /seller/info/:id
GET                 /seller/info
GET                 /seller/list
GET                 /seller/list/:sellerType
GET                 /seller/list/:location/:locationId
GET                 /seller/list/:sellerType/:location/:locationId

# Ad
POST                /ad/submit
PUT                 /ad/submit/:id
PUT                 /ad/cancel/:id
GET                 /ad/info/:id
PUT                 /ad/view/:id
PUT                 /ad/view/phone/:id
GET                 /ad/list
```

Note: for more information, please import this [dump file](https://github.com/ahmadmo/bama-api-demo/blob/master/postman_dump.json) to your [Postman](https://www.getpostman.com/) app.
