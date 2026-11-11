package com.kejing.spatialarcade.ui.feature.arcade
import org.junit.Test
import org.junit.Assert.*
import com.kejing.spatialarcade.domain.model.*
class ArcadeViewModelTest {
 @Test fun contactWindowIsAlwaysOpen(){val vm=ArcadeViewModel(GameKind.A);assertTrue(vm.state.value.selector);vm.onEvent(ArcadeEvent.Demo);assertTrue(vm.state.value.selector)}
 @Test fun selectingIsImmediate(){val vm=ArcadeViewModel(GameKind.A);vm.onEvent(ArcadeEvent.Select(3));assertEquals(3,vm.engine.snapshot.contact);assertTrue(vm.state.value.selector)}
 @Test fun noConfirmNeeded(){val vm=ArcadeViewModel(GameKind.A);vm.onEvent(ArcadeEvent.Select(0));vm.onEvent(ArcadeEvent.Cancel);assertEquals(0,vm.engine.snapshot.contact);assertTrue(vm.state.value.selector)}
 @Test fun invalidPresetIsIgnored(){val vm=ArcadeViewModel(GameKind.A);vm.onEvent(ArcadeEvent.Select(99));assertEquals(2,vm.state.value.pending)}
 @Test fun resetKeepsContact(){val vm=ArcadeViewModel(GameKind.A);vm.onEvent(ArcadeEvent.Select(1));vm.onEvent(ArcadeEvent.Reset);assertEquals(1,vm.engine.snapshot.contact)}
 @Test fun albumMenuOpensFromBookAndSelectionWaitsForPlacement(){val vm=ArcadeViewModel(GameKind.C);assertFalse(vm.state.value.library);vm.onEvent(ArcadeEvent.OpenLibrary);assertTrue(vm.state.value.library);vm.onEvent(ArcadeEvent.Track(1));assertFalse(vm.state.value.library);assertEquals(42,vm.engine.notes.size);assertEquals(Phase.READY,vm.engine.snapshot.phase)}
 @Test fun brickHasNoLevelMenu(){val vm=ArcadeViewModel(GameKind.D);vm.onEvent(ArcadeEvent.Demo);vm.onEvent(ArcadeEvent.OpenLibrary);assertFalse(vm.state.value.library);assertEquals(Phase.PLAYING,vm.engine.snapshot.phase)}
}
