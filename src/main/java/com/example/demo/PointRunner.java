package com.example.demo;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.annotation.WithStateMachine;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.service.DefaultStateMachineService;
import org.springframework.statemachine.service.StateMachineService;
import org.springframework.statemachine.state.State;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Slf4j
@Component
@AllArgsConstructor
public class PointRunner implements ApplicationRunner {
    PointService pointService;
    StateMachine<PointState, PointEvent> machine;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Integer pointId = 10;
        machine.getExtendedState().getVariables().putIfAbsent("pointId", pointId);
        machine.start();
        log.info("state: {}", machine.getState().getId().name());
        machine.sendEvent(PointEvent.催行済み);
        log.info("state: {}", machine.getState().getId().name());

    }
}

enum PointState {
    仮発行,
    有効,
    キャンセル,
    使用済み,
    失効
}

enum PointEvent {
    予約,
    催行済み,
    キャンセル,
    使用,
    期間終了
}

class Point {
    Integer id;
}

@Service
@AllArgsConstructor
class PointService {
    StateMachine<PointState, PointEvent> stateMachine;

    void create() {

    }

}

//@WithStateMachine
//@AllArgsConstructor
//class PointHandler {
//    StateMachine<PointState, PointEvent> stateMachine;
//
//}

@Configuration
@EnableStateMachine
class StateMachineConfig extends EnumStateMachineConfigurerAdapter<PointState, PointEvent> {
    @Override
    public void configure(StateMachineStateConfigurer<PointState, PointEvent> states) throws Exception {
        states.withStates()
                .initial(PointState.仮発行)
                .state(PointState.有効)
                .end(PointState.使用済み)
                .end(PointState.キャンセル)
                .end(PointState.失効);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<PointState, PointEvent> transitions) throws Exception {
        transitions
                .withExternal().source(PointState.仮発行).target(PointState.有効).event(PointEvent.催行済み)
                .and()
                .withExternal().source(PointState.仮発行).target(PointState.キャンセル).event(PointEvent.キャンセル)
                .and()
                .withExternal().source(PointState.有効).target(PointState.使用済み).event(PointEvent.使用)
                .and()
                .withExternal().source(PointState.有効).target(PointState.失効).event(PointEvent.期間終了);
    }
}

