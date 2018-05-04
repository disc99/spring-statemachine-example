package com.example.demo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.Date;
import java.util.UUID;

@Slf4j
//@Component
@AllArgsConstructor
class OrderRunner implements ApplicationRunner {
	StateMachineFactory<OrderStates, OrderEvents> factory;
	OrderService orderService;
	OrderRepository repository;

	@Override
	public void run(ApplicationArguments args) throws Exception {
//		Long orderId = 123L;
//		StateMachine<OrderStates, OrderEvents> machine = factory.getStateMachine(Long.toString(orderId));
//		machine.getExtendedState().getVariables().putIfAbsent("orderId", orderId);
//		machine.start();
//		log.info("🐼 current state: {}", machine.getState().getId().name());
//		machine.sendEvent(OrderEvents.PAY);
//		log.info("🐼 current state: {}", machine.getState().getId().name());
//		Message<OrderEvents> eventsMessage = MessageBuilder
//				.withPayload(OrderEvents.FULFILL)
//				.setHeader("a", "b")
//				.build();
//		machine.sendEvent(eventsMessage);
//		log.info("🐼 current state: {}", machine.getState().getId().name());

		Order order = orderService.create(new Date());
		Long orderId = order.getId();

		StateMachine<OrderStates, OrderEvents> paidStateMachine = orderService.pay(orderId, UUID.randomUUID().toString());
		log.info("🐼 after calling pay(): {}", paidStateMachine.getState().getId().name());
		log.info("🐼 order : {}", repository.findById(orderId));

		StateMachine<OrderStates, OrderEvents> fulfilledStateMachine = orderService.fulfill(orderId);
		log.info("🐼 after calling fulfill(): {}", fulfilledStateMachine.getState().getId().name());
		log.info("🐼 order : {}", repository.findById(orderId));
	}
}

enum OrderEvents {
	FULFILL,
	PAY,
	CANCEL
}

enum OrderStates {
	SUBMITTED,
	PAID,
	FULFILLED,
	CANCELLED
}

@Entity(name = "ORDERS")
@Data
@AllArgsConstructor
@NoArgsConstructor
class Order {
	@Id
	@GeneratedValue
	Long id;
	Date datetime;
	String state;

	public Order(Date datetime, OrderStates state) {
		this.datetime = datetime;
		setOrderState(state);
	}

	public OrderStates getOrderState() {
		return OrderStates.valueOf(state);
	}

	public void setOrderState(OrderStates os) {
		this.state = os.name();
	}
}

interface OrderRepository extends JpaRepository<Order, Long> {}

@Slf4j
@Service
@AllArgsConstructor
class OrderService {
	static final String ORDER_ID_HEADER = "orderId";

	OrderRepository repository;
	StateMachineFactory<OrderStates, OrderEvents> factory;

	Order create(Date when) {
		return repository.save(new Order(when, OrderStates.SUBMITTED));
	}

	StateMachine<OrderStates, OrderEvents> pay(Long orderId, String paymentConfirmNumber) {
		StateMachine<OrderStates, OrderEvents> sm = build(orderId);

		Message<OrderEvents> payMessage = MessageBuilder.withPayload(OrderEvents.PAY)
				.setHeader(ORDER_ID_HEADER, orderId)
				.setHeader("paymentConfirmNumber", paymentConfirmNumber)
				.build();

		sm.sendEvent(payMessage);

		// TODO

		return sm;
	}

	StateMachine<OrderStates, OrderEvents> fulfill(Long orderId) {
		StateMachine<OrderStates, OrderEvents> sm = build(orderId);

		Message<OrderEvents> fulfillMessage = MessageBuilder.withPayload(OrderEvents.FULFILL)
				.setHeader(ORDER_ID_HEADER, orderId)
				.build();

		sm.sendEvent(fulfillMessage);
		return sm;
	}

	private StateMachine<OrderStates, OrderEvents> build(Long orderId) {
		Order order = repository.findById(orderId).orElseGet(null);
		String orderIdKey = Long.toString(order.getId());
		StateMachine<OrderStates, OrderEvents> sm = factory.getStateMachine(orderIdKey);

		sm.stop();
		sm.getStateMachineAccessor()
				.doWithAllRegions(sma -> {
					sma.addStateMachineInterceptor(new StateMachineInterceptorAdapter<OrderStates, OrderEvents>() {
						@Override
						public void preStateChange(State<OrderStates, OrderEvents> state, Message<OrderEvents> message, Transition<OrderStates, OrderEvents> transition, StateMachine<OrderStates, OrderEvents> stateMachine) {
							if (message != null) {
								Long id = Long.class.cast(message.getHeaders().getOrDefault(ORDER_ID_HEADER, -1L));
								if (id != -1L) {
									repository.findById(id)
											.ifPresent(o -> {
												o.setOrderState(state.getId());
												repository.save(o);
											});
								}
							}
						}
					});
					sma.resetStateMachine(new DefaultStateMachineContext<>(order.getOrderState(), null, null, null));
				});
		sm.start();
		return sm;
	}
}

@Slf4j
@Configuration
@EnableStateMachineFactory
class SimpleStateMachineConfiguration extends StateMachineConfigurerAdapter<OrderStates, OrderEvents> {
	@Override
	public void configure(StateMachineTransitionConfigurer<OrderStates, OrderEvents> transitions) throws Exception {
		transitions
				.withExternal().source(OrderStates.SUBMITTED).target(OrderStates.PAID).event(OrderEvents.PAY)
				.and()
				.withExternal().source(OrderStates.PAID).target(OrderStates.FULFILLED).event(OrderEvents.FULFILL)
				.and()
				.withExternal().source(OrderStates.SUBMITTED).target(OrderStates.CANCELLED).event(OrderEvents.CANCEL)
				.and()
				.withExternal().source(OrderStates.PAID).target(OrderStates.CANCELLED).event(OrderEvents.CANCEL);
	}

	@Override
	public void configure(StateMachineStateConfigurer<OrderStates, OrderEvents> states) throws Exception {
		states.withStates()
				.initial(OrderStates.SUBMITTED)
//				.stateEntry(OrderStates.SUBMITTED, context -> {
//                    Long orderId = Long.class.cast(context.getExtendedState().getVariables().getOrDefault("orderId", -1L));
//                    log.info("🐼 orderId is {}.", orderId);
//                    log.info("🐼 entering submitted state!");
//                })
				.state(OrderStates.PAID)
				.end(OrderStates.CANCELLED)
				.end(OrderStates.FULFILLED);
	}

	@Override
	public void configure(StateMachineConfigurationConfigurer<OrderStates, OrderEvents> config) throws Exception {
		StateMachineListenerAdapter<OrderStates, OrderEvents> adapter = new StateMachineListenerAdapter<OrderStates, OrderEvents>() {
			@Override
			public void stateChanged(State<OrderStates, OrderEvents> from, State<OrderStates, OrderEvents> to) {
				log.info("🐼 state changed(from: {}, to: {}", from, to);
			}
		};
		config.withConfiguration()
				.autoStartup(false)
				.listener(adapter);
	}
}