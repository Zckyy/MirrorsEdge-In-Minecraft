//! Faith's attacks against targets: Mirror's Edge's target choice, hit tests, damage and recoil.

use glam::{Vec2, Vec3};

use crate::melee::{clamp_to_cone, pick, Target};
use crate::world::MeshWorld;
use crate::*;

const DT: f32 = 1.0 / 60.0;

fn floor() -> MeshWorld {
    MeshWorld::new(MeshWorld::oriented_box(Vec3::new(0.0, -0.5, 0.0), Vec3::new(60.0, 0.5, 60.0), 0.0), vec![])
}

/// Someone standing `at` (feet), Mirror's Edge-sized (a 30 x 90 uu cylinder).
fn someone(id: u32, at: Vec3) -> Target {
    Target { id, centre: at + Vec3::Y * 0.9, radius: 0.3, half_height: 0.9, eye: 0.6, facing: Vec3::Z }
}

fn standing() -> Controller {
    let mut c = Controller::new(Tuning::default(), Vec3::ZERO, 0.0);
    c.state = State::Ground;
    c
}

/// Step for `secs` with `input(c, t)`, collecting the events.
fn run(c: &mut Controller, w: &MeshWorld, secs: f32, mut input: impl FnMut(&Controller, f32) -> Input) -> Vec<Event> {
    let mut ev = vec![];
    let mut t = 0.0;
    while t < secs {
        let i = input(c, t);
        c.step(DT, &i, w);
        ev.extend(c.events.iter().copied());
        t += DT;
    }
    ev
}

fn hits(ev: &[Event]) -> Vec<(u32, f32)> {
    ev.iter().filter_map(|e| if let Event::MeleeHit { target, damage, .. } = e { Some((*target, *damage)) } else { None }).collect()
}

fn press_once() -> impl FnMut(&Controller, f32) -> Input {
    let mut done = false;
    move |_, _| {
        let p = !done;
        done = true;
        Input { melee_pressed: p, ..Default::default() }
    }
}

#[test]
fn picks_whoever_is_most_in_front() {
    let from = Vec3::Y * 0.9;
    let ahead = someone(1, Vec3::new(0.0, 0.0, -3.0));
    let side = someone(2, Vec3::new(2.0, 0.0, -1.0));
    let behind = someone(3, Vec3::new(0.0, 0.0, 1.0));
    let facing = Vec3::NEG_Z;
    assert_eq!(pick(&[side, ahead, behind], 9.0, from, facing).map(|t| t.id), Some(1));
    // Dead behind scores nothing (the facing term is clamped to zero).
    assert_eq!(pick(&[behind], 9.0, from, facing), None);
}

#[test]
fn a_punch_lands_on_someone_close_in_front_and_misses_further_off() {
    let w = floor();
    for (at, lands) in [(1.2, true), (2.5, false)] {
        let mut c = standing();
        c.targets = vec![someone(7, Vec3::new(0.0, 0.0, -at))];
        let ev = run(&mut c, &w, 1.2, press_once());
        let outcome = ev.iter().find_map(|e| if let Event::MeleeOutcome { hit, .. } = e { Some(*hit) } else { None });
        assert_eq!(outcome, Some(lands), "target {at} m ahead: {ev:?}");
        if lands {
            assert_eq!(hits(&ev), vec![(7, 33.5)]);
        } else {
            assert!(hits(&ev).is_empty());
        }
        assert!(c.melee.is_none(), "the punch ends");
    }
}

#[test]
fn pressing_again_in_the_window_throws_another_punch_with_the_same_hand() {
    let w = floor();
    let mut c = standing();
    let ev = run(&mut c, &w, 2.0, |_, t| Input { melee_pressed: (0.1..0.12).contains(&t) || t == 0.0, ..Default::default() });
    let punches: Vec<bool> = ev.iter().filter_map(|e| if let Event::Melee { left, .. } = e { Some(*left) } else { None }).collect();
    assert_eq!(punches.len(), 2, "{ev:?}");
    assert_eq!(punches[0], punches[1]);
    // Outside the window it's ignored.
    let mut c = standing();
    let ev = run(&mut c, &w, 2.0, |_, t| Input { melee_pressed: t == 0.0 || (0.5..0.52).contains(&t), ..Default::default() });
    assert_eq!(ev.iter().filter(|e| matches!(e, Event::Melee { .. })).count(), 1);
}

#[test]
fn a_running_jump_kick_lands_and_bounces_her_back() {
    let w = floor();
    let mut c = standing();
    c.targets = vec![someone(3, Vec3::new(0.0, 0.0, -6.0))];
    let mut jumped = false;
    let mut air = 0;
    let mut kicked = false;
    let mut bounced = false;
    let ev = run(&mut c, &w, 3.0, |c, _| {
        let jump = !jumped && c.horizontal_speed() > 5.0;
        jumped |= jump;
        air = if c.state == State::Air { air + 1 } else { 0 };
        let kick = !kicked && air > 8;
        kicked |= kick;
        if c.melee_no_input {
            bounced = true;
        }
        Input { move_axis: Vec2::new(0.0, 1.0), jump_pressed: jump, melee_pressed: kick, ..Default::default() }
    });
    let h = hits(&ev);
    assert_eq!(h.len(), 1, "{ev:?}");
    assert!(h[0].1 >= 60.0 && h[0].1 <= 100.0, "damage {}", h[0].1);
    assert!(bounced, "knocked back off them");
}

#[test]
fn a_slide_kick_lands_once_its_hit_detection_comes_on() {
    let w = floor();
    let mut c = standing();
    c.targets = vec![someone(5, Vec3::new(0.0, 0.0, -9.0))];
    let mut slid = false;
    let mut kicked = false;
    let ev = run(&mut c, &w, 3.0, |c, _| {
        let slide = !slid && c.horizontal_speed() > 5.5;
        slid |= slide;
        let kick = !kicked && matches!(c.state, State::Slide { .. });
        kicked |= kick;
        Input { move_axis: Vec2::new(0.0, 1.0), crouch_pressed: slide, crouch_held: slid, melee_pressed: kick, ..Default::default() }
    });
    assert_eq!(hits(&ev), vec![(5, 60.0)], "{ev:?}");
}

#[test]
fn the_sweep_direction_keeps_the_games_cone_quirk() {
    let f = Vec3::NEG_Z;
    // 45 degrees off: cos 0.707 is over 35 degrees in radians (0.611), so it's kept.
    let v45 = Vec3::new(1.0, 0.0, -1.0);
    assert!((clamp_to_cone(v45, f, 35.0) - v45).length() < 1e-5);
    // 60 degrees off: turned onto the cone's edge, its length kept.
    let v60 = Vec3::new(60f32.to_radians().sin(), 0.0, -60f32.to_radians().cos()) * 2.0;
    let c = clamp_to_cone(v60, f, 35.0);
    assert!((c.length() - 2.0).abs() < 1e-4);
    assert!(c.normalize().dot(f) > v60.normalize().dot(f));
}

/// A Minecraft mob: `radius` wide and `half_height` high, standing `at` (feet).
fn mob(id: u32, at: Vec3, radius: f32, half_height: f32) -> Target {
    Target { id, centre: at + Vec3::Y * half_height, radius, half_height, eye: half_height * 0.8, facing: Vec3::Z }
}

fn punch_outcome(targets: Vec<Target>) -> (Option<bool>, Vec<(u32, f32)>) {
    let w = floor();
    let mut c = standing();
    c.targets = targets;
    let ev = run(&mut c, &w, 1.2, press_once());
    (ev.iter().find_map(|e| if let Event::MeleeOutcome { hit, .. } = e { Some(*hit) } else { None }), hits(&ev))
}

#[test]
fn a_punch_reaches_the_side_of_a_big_mob_not_its_middle() {
    // A spider (1.4 wide): its middle 2 m off, its side 1.3 m.
    assert_eq!(punch_outcome(vec![mob(1, Vec3::new(0.0, 0.0, -2.0), 0.7, 0.45)]).0, Some(true));
    // A chicken, small and low, at arm's length.
    assert_eq!(punch_outcome(vec![mob(2, Vec3::new(0.0, 0.0, -1.0), 0.2, 0.35)]).0, Some(true));
}

#[test]
fn a_punch_lands_on_a_mob_a_little_off_to_the_side() {
    // 40 degrees off centre (the game's 0.8 cone is 37), but its near side well inside.
    let a = 40f32.to_radians();
    assert_eq!(punch_outcome(vec![mob(3, Vec3::new(a.sin(), 0.0, -a.cos()) * 1.2, 0.3, 0.95)]).0, Some(true));
    // Off to the side, not in front: a miss.
    assert_eq!(punch_outcome(vec![mob(4, Vec3::new(1.2, 0.0, 0.0), 0.3, 0.95)]).0, Some(false));
}

#[test]
fn a_punch_lands_on_whoever_is_in_reach_if_the_target_is_not() {
    // Picked: the one dead ahead, far off. In reach: one just off to the side.
    let far = mob(5, Vec3::new(0.0, 0.0, -6.0), 0.3, 0.95);
    let near = mob(6, Vec3::new(0.9, 0.0, -0.8), 0.3, 0.95);
    assert_eq!(pick(&[far, near], 9.0, Vec3::Y * 0.9, Vec3::NEG_Z).map(|t| t.id), Some(5), "the far one is picked");
    let (outcome, h) = punch_outcome(vec![far, near]);
    assert_eq!(outcome, Some(true));
    assert_eq!(h, vec![(6, 33.5)]);
}
