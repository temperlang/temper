$(document).ready(function(){
  // kenwheeler.github.io/slick/ enables the use-case carousel.
  $('#use-case-carousel').slick({
    slidesToScroll: 1,
    autoplay: true,
    autoplaySpeed: 10000, // 10s in ms
    dots: true,
    infinite: true,
    pauseOnHover: true,
    pauseOnDotsHover: true,
    // adaptiveHeight: true,
  });
});
