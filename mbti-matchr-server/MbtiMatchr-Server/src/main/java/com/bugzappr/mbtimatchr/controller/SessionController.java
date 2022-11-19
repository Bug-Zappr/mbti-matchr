package com.bugzappr.mbtimatchr.controller;


import com.bugzappr.mbtimatchr.dto.QueueResponse;
import com.bugzappr.mbtimatchr.model.QueuePlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping("/api")
public class SessionController {
  private ConcurrentLinkedDeque<QueuePlayer> queue = new ConcurrentLinkedDeque<QueuePlayer>();
  private ExecutorService matchers = Executors.newFixedThreadPool(6);

  @PostMapping("/join")
  public DeferredResult<QueueResponse> join(@RequestParam String mbti) {
    DeferredResult<QueueResponse> output = new DeferredResult<>(20000L);
    QueuePlayer player = new QueuePlayer(mbti, UUID.randomUUID());
    output.onTimeout(() -> {queue.remove(player); output.setErrorResult("please try later");});
    matchers.execute(() -> {
      synchronized (player) {
        if (queue.isEmpty()) {
          try {
            queue.add(player);
            player.wait();
            queue.remove(player);
            output.setResult(
                new QueueResponse(player.getUuid(), player.getMatch().getMbti(), player.getMatch().getUuid(), "127.0.0.1", 8080, 0));
          } catch (Exception e) {
            System.out.println("1 " + e.getMessage());
            output.setErrorResult(e.getMessage());
          }
        } else {
          try {
            boolean found = false;
            for(QueuePlayer p : queue) {
              if (((ArrayList<String>) MBTIMapping.mapping.get(mbti)).contains(p.getMbti())) {
                found = true;
                p.setMatch(player);
                player.setMatch(p);
                synchronized (p) {
                  p.notify();
                }
                queue.remove(player);
                output.setResult(
                    new QueueResponse(player.getUuid(), player.getMatch().getMbti(), player.getMatch().getUuid(), "127.0.0.1", 8080, 0));
              }
            }
            if(!found) {
              queue.add(player);
              player.wait();
              queue.remove(player);
              output.setResult(
                  new QueueResponse(player.getUuid(), player.getMatch().getMbti(), player.getMatch().getUuid(), "127.0.0.1", 8080, 0));
            }
          } catch (Exception e) {
            System.out.println("2" + e);
            output.setErrorResult(e.getMessage());
          }
        }
      }
    });
    return output;
  }
}
