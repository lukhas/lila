package lila.team

import akka.stream.scaladsl._
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api.ReadPreference
import scala.concurrent.duration._

import lila.common.config.MaxPerSecond
import lila.db.dsl._
import lila.user.{ User, UserRepo }

final class TeamMemberStream(
    memberRepo: MemberRepo,
    userRepo: UserRepo
)(implicit mat: akka.stream.Materializer) {

  def apply(team: Team, perSecond: MaxPerSecond): Source[User, _] =
    memberRepo.coll
      .ext.find($doc("team" -> team.id), $doc("user" -> true))
      .sort($sort desc "date")
      .batchSize(perSecond.value)
      .cursor[Bdoc](ReadPreference.secondaryPreferred)
      .documentSource()
      .grouped(perSecond.value)
      .delay(1 second)
      .map(_.flatMap(_.getAsOpt[User.ID]("user")))
      .mapAsync(1)(userRepo.usersFromSecondary)
      .mapConcat(identity)
}
