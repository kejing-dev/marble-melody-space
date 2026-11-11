package com.kejing.spatialarcade.domain.usecase
import org.junit.Test
import org.junit.Assert.*
import java.io.File
class MusicCascadeDiagnosticTest {
 @Test fun reportAllCourses(){
  val rows=mutableListOf("track,contact,time,error_m,failed,reason")
  var failures=0
  for(k in 0..19){val s=MusicSimulation(MusicCourses.get(k));s.place()
   repeat(((s.course.runSeconds+3)*480).toInt()){
    s.step(1f/480)
    while(s.events.isNotEmpty()){val i=s.events.removeFirst();rows+="$k,$i,${s.impactTime},${MusicCourse.distance(s.impactPosition,s.course.impacts[i].contact)},${s.missed},${s.failureReason}"}
   }
   if(!s.completed){failures++;rows+="$k,END,${s.time},0,${s.missed},${s.failureReason}"}
  }
  val cwd=File(System.getProperty("user.dir"));val root=if(cwd.name=="app")cwd.parentFile else cwd
  File(root,"build/reports/simulation/v027-contact-diagnostic.csv").apply{requireNotNull(parentFile).mkdirs()}.writeText(rows.joinToString("\n"))
  assertEquals("All physical cascades finish",0,failures)
 }
}
