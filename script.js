document.getElementById('year').textContent = new Date().getFullYear();

const ctaButtons = document.querySelectorAll('.cta-button, .secondary-button');

ctaButtons.forEach((button) => {
  button.addEventListener('click', () => {
    button.classList.add('pulse');
    setTimeout(() => button.classList.remove('pulse'), 200);
  });
});
